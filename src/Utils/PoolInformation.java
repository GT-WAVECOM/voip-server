package Utils;

import java.util.Map;

public class PoolInformation {
    private static final String TAG = "PoolInformation";
    private IPEndPoint endPoint;
    private DeviceType deviceType;
    private boolean isCallGoingOn;

    // constructor for the ESP32
    public PoolInformation(IPEndPoint endPoint, DeviceType deviceType) {
        this.endPoint = endPoint;
        this.deviceType = deviceType;
        isCallGoingOn = false;
    }

    public IPEndPoint getEndPoint() {
        return endPoint;
    }

    public void setEndPoint(IPEndPoint endPoint) {
        this.endPoint = endPoint;
    }

    public DeviceType getDeviceType() {
        return deviceType;
    }

    public boolean isCallGoingOn() {
        return isCallGoingOn;
    }

    public void setCallGoingOn(boolean callGoingOn) {
        isCallGoingOn = callGoingOn;
    }
}
