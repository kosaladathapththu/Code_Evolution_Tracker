package model;

public class Version {
    public final int versionId;
    public final long timestamp;
    public final String codeText;
    public final String note;
    public final String errorType;
    public boolean bugFree;

    public Version(int versionId, long timestamp, String codeText, String note, String errorType) {
        this.versionId = versionId;
        this.timestamp = timestamp;
        this.codeText = codeText;
        this.note = note;
        this.errorType = errorType;
        this.bugFree = false;
    }
}