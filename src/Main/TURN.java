package Main;

import Utils.DebugMessage;
import Utils.DeviceType;
import Utils.IPEndPoint;
import Utils.PoolInformation;

import java.net.*;
import java.util.HashMap;
import java.util.Map;

public class TURN  implements Runnable {
    private static final String TAG = "TURN";
    private DatagramSocket turnSocket;
    private Map<String, PoolInformation> poolQueue;
    private String pool;
    private String deviceString;
    private String phoneString;
    private IPEndPoint deviceEndPoint;
    private IPEndPoint phoneEndPoint;
    private Map<String, IPEndPoint> turnInternalPool;

    public TURN(DatagramSocket turnSocket,
                PoolInformation deviceInfo, PoolInformation phoneInfo,
                String pool, Map<String, PoolInformation> poolQueue) {
        this.turnSocket = turnSocket;
        this.poolQueue = poolQueue;
        this.pool = pool;

        // double check the information corresponding is correct
        if (deviceInfo.getDeviceType() == DeviceType.ESP32) {
            deviceEndPoint = deviceInfo.getEndPoint();
            phoneEndPoint = phoneInfo.getEndPoint();
        } else {
            deviceEndPoint = phoneInfo.getEndPoint();
            phoneEndPoint = deviceInfo.getEndPoint();
        }
        deviceString = deviceEndPoint.toString();
        phoneString = phoneEndPoint.toString();

        turnInternalPool = new HashMap<>();
        turnInternalPool.put(deviceString, phoneEndPoint);
        turnInternalPool.put(phoneString, deviceEndPoint);
    }

    @Override
    public void run() {
        DebugMessage.log(TAG, "====== turn server start ======");
        DatagramPacket incomingPacket;
        DatagramPacket outgoingPacket;
        byte[][] bufferList = new byte[100][1024];
        int bufferListIndex = 0;
        try {
            turnSocket.setSoTimeout(2500);
            InetAddress incomingAddress;
            int incomingPort;
            IPEndPoint incomingEndPoint;
            IPEndPoint outgoingEndPoint;
            boolean isTurnForwarding = true;
            int errorMsgCount = 0;
            DebugMessage.log(TAG, String.format("Device: %s, Phone: %s", deviceString, phoneString));

            while (isTurnForwarding) {
                incomingPacket = new DatagramPacket(bufferList[bufferListIndex], bufferList[bufferListIndex].length);
                turnSocket.receive(incomingPacket);
                incomingAddress = incomingPacket.getAddress();
                incomingPort = incomingPacket.getPort();
                incomingEndPoint = new IPEndPoint(incomingAddress, incomingPort);

                String msg = null;

                if (incomingPacket.getLength() < 10) {
                    msg = new String(incomingPacket.getData(), 0, incomingPacket.getLength());
                }

                if (msg != null && msg.startsWith("LC Stop")) {
                    DebugMessage.log(TAG, "Terminate call request received, cleaning pool...");
                    turnInternalPool.clear();
                } else {
                    // heart beat, check the address info
                    if (msg != null && msg.length() == 1) {
                        boolean isInfoChanged = false;
                        if (msg.equals("1")) {
                            if (!incomingEndPoint.equals(deviceEndPoint)) {
                                deviceEndPoint = incomingEndPoint;
                                deviceString = deviceEndPoint.toString();
                                DebugMessage.log(TAG, "Device port changed to " + deviceString);
                                isInfoChanged = true;
                            }
                        } else if (msg.equals("2")) {
                            if (!incomingEndPoint.equals(phoneEndPoint)) {
                                phoneEndPoint = incomingEndPoint;
                                phoneString = phoneEndPoint.toString();
                                DebugMessage.log(TAG, "Phone port changed to " + phoneString);
                                isInfoChanged = true;
                            }
                        }
                        if (isInfoChanged) {
                            turnInternalPool.clear();
                            turnInternalPool.put(deviceString, phoneEndPoint);
                            turnInternalPool.put(phoneString, deviceEndPoint);
                        }
                    }

                    outgoingEndPoint = turnInternalPool.get(incomingEndPoint.toString());
                    if (outgoingEndPoint == null) {
                        byte[] response = "LC Stop\0".getBytes();
                        DatagramPacket resp = new DatagramPacket(response, response.length, incomingEndPoint.getIpAddress(), incomingEndPoint.getPort());
                        turnSocket.send(resp);
                        DebugMessage.log(TAG, "Symmetric call ends, waiting for turn port timeout!");
                        errorMsgCount++;
                        if (errorMsgCount > 10) {
                            DebugMessage.log(TAG, "Turn port time out, closing...\n");
                            turnSocket.close();
                            poolQueue.remove(pool);
                            isTurnForwarding = false;
                        }
                    } else {
                        outgoingPacket = new DatagramPacket(bufferList[bufferListIndex], incomingPacket.getLength(),
                                outgoingEndPoint.getIpAddress(), outgoingEndPoint.getPort());
                        turnSocket.send(outgoingPacket);
                    }
                }

                bufferListIndex = (bufferListIndex + 1) % 100;
            }
        } catch (SocketTimeoutException e) {
            DebugMessage.log(TAG, "Turn server on port " + turnSocket.getLocalPort() + " time out\n");
        } catch (Exception e) {
            DebugMessage.log(TAG, "Turn server on port " + turnSocket.getLocalPort() + " error\n");
        } finally {
            turnSocket.close();
            poolQueue.remove(pool);
        }
    }
}
