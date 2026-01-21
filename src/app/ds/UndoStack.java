package ds;

public class UndoStack {
    private static class SNode {
        VersionNode ref;
        SNode next;
        SNode(VersionNode ref, SNode next) { this.ref = ref; this.next = next; }
    }
    private SNode top;

    public void push(VersionNode ref) { top = new SNode(ref, top); }
    public VersionNode pop() {
        if (top == null) return null;
        VersionNode v = top.ref;
        top = top.next;
        return v;
    }
    public boolean isEmpty() { return top == null; }
}
