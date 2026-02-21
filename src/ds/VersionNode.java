package ds;

import model.Version;

public class VersionNode {
    public Version data;
    public VersionNode next;
    public VersionNode prev;

    public VersionNode(Version data) {
        this.data = data;
    }
}
