package Utils;

import java.util.Map;

public class PoolInformation {
    private static final String TAG = "PoolInformation";
    private IPEndPoint endPoint;
    private DeviceType deviceType;
    private NATType natType;
    private boolean isESP32Joined;

    // constructor for phone
    public PoolInformation(IPEndPoint endPoint, DeviceType deviceType, NATType natType, String pool, Map<String, PoolInformation> mainQueuePool) {
        this(endPoint, deviceType, natType);

        //set the timeout operation
        if (deviceType == DeviceType.MobilePhone) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        Thread.sleep(15000);
                        if (!PoolInformation.this.isESP32Joined) {
                            DebugMessage.log(TAG, "Request for " + pool + " timeout...");
                            mainQueuePool.remove(pool);
                        }
                    } catch (InterruptedException e) {
                        DebugMessage.log(TAG, "Time out interrupted");
                        e.printStackTrace();
                    }
                }
            }).start();
        }
    }

    // constructor for the ESP32
    public PoolInformation(IPEndPoint endPoint, DeviceType deviceType, NATType natType) {
        this.endPoint = endPoint;
        this.natType = natType;
        this.deviceType = deviceType;
        isESP32Joined = false;
    }

    public IPEndPoint getEndPoint() {
        return endPoint;
    }

    public void setEndPoint(IPEndPoint endPoint) {
        this.endPoint = endPoint;
    }

    public NATType getNatType() {
        return natType;
    }

    public void setNatType(NATType natType) {
        this.natType = natType;
    }

    public DeviceType getDeviceType() {
        return deviceType;
    }

    public void setDeviceType(DeviceType deviceType) {
        this.deviceType = deviceType;
    }

    public void setESP32Joined(boolean joined) {
        this.isESP32Joined = joined;
    }

    public boolean isESP32Joined() {
        return isESP32Joined;
    }
}
