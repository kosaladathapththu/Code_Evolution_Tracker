package ds;

import java.util.ArrayList;
import java.util.List;

public class ErrorBST {
    private ErrorNode root;

    public void addError(String errorType) {
        if (errorType == null) errorType = "Unknown";
        root = addRec(root, errorType.trim());
    }

    private ErrorNode addRec(ErrorNode cur, String key) {
        if (cur == null) return new ErrorNode(key);
        int cmp = key.compareToIgnoreCase(cur.key);
        if (cmp < 0) cur.left = addRec(cur.left, key);
        else if (cmp > 0) cur.right = addRec(cur.right, key);
        else cur.count++;
        return cur;
    }

    public List<String> inorderPairs() {
        List<String> out = new ArrayList<>();
        inorder(root, out);
        return out;
    }

    private void inorder(ErrorNode cur, List<String> out) {
        if (cur == null) return;
        inorder(cur.left, out);
        out.add(cur.key + ":" + cur.count);
        inorder(cur.right, out);
    }

    public String mostFrequent() {
        if (root == null) return null;
        return mostRec(root, new ErrorNode[]{null}).key;
    }

    private ErrorNode mostRec(ErrorNode cur, ErrorNode[] best) {
        if (cur == null) return best[0];
        if (best[0] == null || cur.count > best[0].count) best[0] = cur;
        mostRec(cur.left, best);
        mostRec(cur.right, best);
        return best[0];
    }
}
