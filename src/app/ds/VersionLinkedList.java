package ds;

import model.Version;
import java.util.ArrayList;
import java.util.List;

public class VersionLinkedList {

    private VersionNode head;
    private VersionNode tail;
    private VersionNode current;

    public VersionNode add(Version v) {
        VersionNode n = new VersionNode(v);
        if (head == null) {
            head = tail = current = n;
        } else {
            tail.next = n;
            n.prev = tail;
            tail = n;
            current = n;
        }
        return n;
    }

    public VersionNode getCurrentNode() {
        return current;
    }

    public Version getCurrent() {
        return current == null ? null : current.data;
    }

    public void setCurrent(VersionNode n) {
        current = n;
    }

    public VersionNode findById(int id) {
        VersionNode t = head;
        while (t != null) {
            if (t.data.versionId == id) return t;
            t = t.next;
        }
        return null;
    }

    public List<Version> toList() {
        List<Version> list = new ArrayList<>();
        VersionNode t = head;
        while (t != null) {
            list.add(t.data);
            t = t.next;
        }
        return list;
    }

    public VersionNode lastBugFreeFromCurrent() {
        VersionNode t = current;
        while (t != null) {
            if (t.data.bugFree) return t;
            t = t.prev;
        }
        return null;
    }
}
