package Main;

import Utils.IPEndPoint;
import Utils.PoolInformation;

import java.util.HashMap;
import java.util.Map;

public class VoipServer {
    private String serverPublicIP;
    private int numOfSTUN;
    private boolean[] stunPoolStatus;
    private Map<String, PoolInformation> poolQueue;

    public VoipServer(String serverPublicIP, int numOfSTUN) {
        this.serverPublicIP = serverPublicIP;
        this.numOfSTUN = numOfSTUN;
        stunPoolStatus = new boolean[numOfSTUN];
        poolQueue = new HashMap<>();
    }

    public void serverInit() {
        for (int i = 0; i < numOfSTUN; i++) {
            STUN stun = new STUN(7000 + i * 1000, stunPoolStatus, poolQueue,
                    serverPublicIP, i);
            new Thread(stun).start();
        }
    }

    public static void main(String[] args) {
        VoipServer server = new VoipServer("54.83.79.129", 10);
        server.serverInit();
    }
}
