package Utils;

public enum NATType {
    FullCone,
    RestrictedCone,
    PortRestrictedCone,
    Symmetric,
    Unknown;

    public static NATType getNatType(String id) {
        switch(id) {
            case "0":
                return FullCone;
            case "1":
                return RestrictedCone;
            case "2":
                return PortRestrictedCone;
            case "3":
                return Symmetric;
                default:
                    return Unknown;
        }
    }
}
