package Utils;

public enum DeviceType {
    ESP32,
    MobilePhone;

    public static DeviceType getDeviceType(String id) {
        switch(id) {
            case "1":
                return ESP32;
            case "2":
                return MobilePhone;
                default:
                    return null;
        }
    }
}
