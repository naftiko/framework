package io.naftiko.cli.enums;

public enum CapabilityType {
    REST_ADAPTER("Rest Adapter", "restAdapter"),
    PASS_THRU("Pass Thru", "passThru"),
    UNKNOWN("Unknown","unknown");

    public final String label;
    public final String pathName;

    private CapabilityType(String label, String pathName) {
        this.label = label;
        this.pathName = pathName;
    }

    public static CapabilityType valueOfLabel(String label) {
        for (CapabilityType capabilityType : values()) {
            if (java.util.Objects.equals(capabilityType.label, label)) {
                return capabilityType;
            }
        }
        return CapabilityType.UNKNOWN;
    }
}
