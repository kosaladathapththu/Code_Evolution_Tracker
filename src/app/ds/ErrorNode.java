package ds;

public class ErrorNode {
    public String key;   // errorType
    public int count;
    public ErrorNode left, right;

    public ErrorNode(String key) {
        this.key = key;
        this.count = 1;
    }
}
