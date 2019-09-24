package Utils;

import java.net.InetAddress;

public class IPEndPoint {
    public static final int MAXPORT = 0x0000FFFF;
    public static final int MINPORT = 0x00000000;

    private InetAddress ipAddress;
    private int port;

    public IPEndPoint(InetAddress ipAddress, int port) {
        this.ipAddress = ipAddress;
        this.port = port;
    }

    public boolean equals(IPEndPoint endPoint) {
        return endPoint.getIpAddress().equals(this.ipAddress) && endPoint.getPort() == this.port;
    }

    public InetAddress getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(InetAddress ipAddress) {
        this.ipAddress = ipAddress;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    @Override
    public String toString() {
        return ipAddress.toString() + port;
    }
}
