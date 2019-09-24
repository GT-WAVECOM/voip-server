package Main;

import Utils.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Map;
import java.util.Random;

public class STUN implements Runnable {
    private static final String TAG = "STUN";
    private int listeningPort;
    private boolean[] stunPoolStatus;
    private int stunPoolIndex;
    private String selfIP;
    private Map<String, PoolInformation> poolQueue;

    public STUN(int port, boolean[] stunPoolStatus,
                Map<String, PoolInformation> poolQueue,
                String selfIP, int index) {
        listeningPort = port;
        this.stunPoolStatus = stunPoolStatus;
        this.selfIP = selfIP;
        stunPoolIndex = index;
        this.poolQueue = poolQueue;
    }

    public static byte[] addr2bytes(IPEndPoint endPoint, NATType natType) {
        ArrayList<Byte> bytes = new ArrayList<>();
        // serialize ip address
        for (byte b : endPoint.getIpAddress().getAddress()) {
            bytes.add(b);
        }
        // serialize port & nat type
        ByteBuffer byteBuffer = ByteBuffer.allocate(4);
        byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
        short port = (short) endPoint.getPort();
        byteBuffer.putShort(port);

        short nat = (short) natType.ordinal();
        byteBuffer.putShort(nat);

        for (byte b : byteBuffer.array()) {
            bytes.add(b);
        }

        byte[] res = new byte[bytes.size()];
        for (int i = 0; i < res.length; i++) {
            res[i] = bytes.get(i);
        }

        return res;
    }

    @Override
    public void run() {
        try {
            // binding the stun to given port
            DebugMessage.log(TAG, "STUN Socket is trying to bind to port: " + listeningPort);
            DatagramSocket stunSocket = new DatagramSocket(listeningPort);
            stunSocket.setReuseAddress(true);
            stunPoolStatus[stunPoolIndex] = true;

            //start listening to the port
            byte[] buffer = new byte[1024];
            while (true) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                stunSocket.receive(packet);
                String msg = new String(packet.getData(), 0, packet.getLength());
                IPEndPoint incomingAddress = new IPEndPoint(packet.getAddress(), packet.getPort());

                if (msg.startsWith("del")) { // voice call cancel request
                    DebugMessage.log(TAG, "Communication cancel requested");
                    String pool = msg.substring(4);
                    poolQueue.remove(pool);
                    byte[] response = "cancel!!".getBytes();
                    DatagramPacket resp = new DatagramPacket(response, response.length, incomingAddress.getIpAddress(), incomingAddress.getPort());
                    stunSocket.send(resp);
                    DebugMessage.log(TAG, "cancel request before connecting");
                } else {
                    DebugMessage.log(TAG, String.format("Connection from %s:%d", incomingAddress.getIpAddress().toString(), incomingAddress.getPort()));

                    String[] data = msg.trim().split(" ", 0);
                    if (data.length != 3) {
                        DebugMessage.log(TAG, "Malformed request, discarded...");
                        continue;
                    }

                    String pool = data[0];
                    NATType natType = NATType.getNatType(data[1]);
                    DeviceType deviceType = DeviceType.getDeviceType(data[2]);
                    DebugMessage.log(TAG, String.format("Stun server %d received request from %s for pool \"%s\"", this.stunPoolIndex, data[2], pool));

                    if (!poolQueue.containsKey(pool)) {
                        byte[] resp;
                        if (deviceType == DeviceType.ESP32) {
                            resp = "canceled".getBytes();
                            DatagramPacket respPacket = new DatagramPacket(resp, resp.length, incomingAddress.getIpAddress(), incomingAddress.getPort());
                            stunSocket.send(respPacket);
                            continue;
                        }

                        PoolInformation info = new PoolInformation(incomingAddress, deviceType, natType, pool, poolQueue);
                        poolQueue.put(pool, info);

                        resp = "goodtogo".getBytes();
                        DatagramPacket respPacket = new DatagramPacket(resp, resp.length, incomingAddress.getIpAddress(), incomingAddress.getPort());
                        stunSocket.send(respPacket);
                    } else {
                        PoolInformation peerInfo = poolQueue.get(pool);
                        // if the request exits, decline another request from phone
                        if (deviceType == DeviceType.MobilePhone) {
                            DebugMessage.log(TAG, "Device occupied");
                            byte[] resp = "occupied".getBytes();
                            DatagramPacket respPacket = new DatagramPacket(resp, resp.length, incomingAddress.getIpAddress(), incomingAddress.getPort());
                            stunSocket.send(respPacket);
                            continue;
                        }

                        // at least on side on not fullcone
                        if (natType != NATType.FullCone || peerInfo.getNatType() != NATType.FullCone) {
                            if (!peerInfo.isESP32Joined()) {
                                // initiate the turn socket and share with device and phone
                                Random rand = new Random();
                                DatagramSocket turnSocket = null;
                                boolean isTurnPortValid = false;
                                int turnPort = listeningPort + 1;

                                while (!isTurnPortValid) {
                                    try {
                                        turnPort = listeningPort + rand.nextInt(1000) + 1;
                                        DebugMessage.log(TAG, "Turn server is trying to bind to port: " + turnPort);
                                        turnSocket = new DatagramSocket(turnPort);
                                        isTurnPortValid = true;
                                        turnSocket.setReuseAddress(true);
                                    } catch (SocketException e) {
                                        DebugMessage.log(TAG, "Turn port " + turnPort + " is not available, retrying...");
                                    }
                                }

                                DebugMessage.log(TAG, "Turn server is listening on port: " + turnSocket.getLocalPort());

                                InetAddress turnAddress = InetAddress.getByName(selfIP);
                                byte[] turnBuf = addr2bytes(new IPEndPoint(turnAddress, turnPort), NATType.FullCone);

                                DatagramPacket peerPacket = new DatagramPacket(turnBuf, turnBuf.length,
                                        incomingAddress.getIpAddress(), incomingAddress.getPort());

                                DatagramPacket currPacket = new DatagramPacket(turnBuf, turnBuf.length,
                                        peerInfo.getEndPoint().getIpAddress(), peerInfo.getEndPoint().getPort());

                                stunSocket.send(peerPacket);
                                stunSocket.send(currPacket);

                                // initiate the turn thread
                                DebugMessage.log(TAG, "Hurray! symmetric chat link established.");
                                DebugMessage.log(TAG, "======== transfer to turn server =======\n");

                                peerInfo.setEndPoint(new IPEndPoint(turnAddress, turnPort));
                                peerInfo.setNatType(NATType.FullCone);
                                peerInfo.setESP32Joined(true);
                                TURN turn = new TURN(turnSocket, new PoolInformation(incomingAddress, deviceType, natType), peerInfo, pool, poolQueue);
                                Thread turnThread = new Thread(turn);
                                turnThread.setDaemon(true);
                                turnThread.start();

                            } else { // voice communication has been initiated, handle the device retry request
                                DebugMessage.log(TAG, "Retry request for the TURN server from " + data[2]);
                                PoolInformation currInfo = poolQueue.get(pool);
                                byte[] turnBuf = addr2bytes(currInfo.getEndPoint(), NATType.FullCone);
                                DatagramPacket currPacket = new DatagramPacket(turnBuf, turnBuf.length,
                                        incomingAddress.getIpAddress(), incomingAddress.getPort());
                                stunSocket.send(currPacket);
                            }
                        } else { // both are fullcone
                            byte[] peerBuf = addr2bytes(peerInfo.getEndPoint(), peerInfo.getNatType());
                            byte[] currBuf = addr2bytes(incomingAddress, natType);
                            DatagramPacket peerPacket = new DatagramPacket(peerBuf, peerBuf.length,
                                    incomingAddress.getIpAddress(), incomingAddress.getPort());
                            DatagramPacket currPacket = new DatagramPacket(currBuf, currBuf.length,
                                    peerInfo.getEndPoint().getIpAddress(), peerInfo.getEndPoint().getPort());

                            stunSocket.send(peerPacket);
                            stunSocket.send(currPacket);

                            DebugMessage.log(TAG, "Linked");
                            poolQueue.remove(pool);
                        }
                    }
                }
            }
        } catch (SocketException e) {
            System.out.println("Binding to port " + listeningPort + " fail");
            e.printStackTrace();
        } catch (Exception e) {
            System.out.println("Other error in Stun");
            e.printStackTrace();
        } finally {
            stunPoolStatus[stunPoolIndex] = false;
        }
    }
}
