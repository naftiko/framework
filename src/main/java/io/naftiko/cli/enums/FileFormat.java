package io.naftiko.cli.enums;

public enum FileFormat {
    JSON("Json","json"),
    YAML("Yaml","yaml"),
    UNKNOWN("Unknown","unknown");

    public final String label;
    public final String pathName;

    private FileFormat(String label, String pathName) {
        this.label = label;
        this.pathName = pathName;
    }

    public static FileFormat valueOfLabel(String label) {
        for (FileFormat fileFormat : values()) {
            if (java.util.Objects.equals(fileFormat.label, label)) {
                return fileFormat;
            }
        }
        return FileFormat.UNKNOWN;
    }
}
