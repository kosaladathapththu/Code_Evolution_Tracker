package app;

import com.sun.net.httpserver.*;
import ds.*;
import model.Version;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.ArrayList;

public class ApiServer {

    private static final VersionLinkedList history = new VersionLinkedList();
    private static final UndoStack undo = new UndoStack();
    private static final ErrorBST errorTree = new ErrorBST();
    private static int nextId = 1;

    public static void main(String[] args) throws Exception {
        int port = 8080;
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        // POST /step
        server.createContext("/step", ex -> {
            addCors(ex);
            if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) { ok(ex, ""); return; }
            if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) { methodNotAllowed(ex); return; }

            String body = readBody(ex);
            String code = getJsonString(body, "codeText");
            String note = getJsonString(body, "note");
            String errorType = getJsonString(body, "errorType");

            if (code == null || code.trim().isEmpty()) {
                bad(ex, "codeText required");
                return;
            }

            Version v = new Version(
                    nextId++,
                    System.currentTimeMillis(),
                    code,
                    note == null ? "" : note,
                    errorType == null ? "Unknown" : errorType
            );

            VersionNode node = history.add(v);
            undo.push(node);
            errorTree.addError(v.errorType);

            ok(ex, "{\"message\":\"saved\",\"versionId\":" + v.versionId + "}");
        });

        // GET /timeline
        server.createContext("/timeline", ex -> {
            addCors(ex);
            if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) { ok(ex, ""); return; }
            if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) { methodNotAllowed(ex); return; }
            ok(ex, toJsonTimeline(history.toList(), history.getCurrent()));
        });

        // POST /undo
        server.createContext("/undo", ex -> {
            addCors(ex);
            if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) { ok(ex, ""); return; }
            if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) { methodNotAllowed(ex); return; }

            VersionNode cur = undo.pop();
            if (cur == null || cur.prev == null) {
                bad(ex, "Nothing to undo");
                return;
            }
            history.setCurrent(cur.prev);
            ok(ex, toJsonCurrent(history.getCurrent()));
        });

        // POST /markBugFree?id=#
        server.createContext("/markBugFree", ex -> {
            addCors(ex);
            if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) { ok(ex, ""); return; }
            if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) { methodNotAllowed(ex); return; }

            String idStr = queryParam(ex.getRequestURI().getQuery(), "id");
            if (idStr == null) { bad(ex, "id required"); return; }

            int id = Integer.parseInt(idStr);
            VersionNode n = history.findById(id);
            if (n == null) { bad(ex, "version not found"); return; }

            n.data.bugFree = true;
            ok(ex, "{\"message\":\"marked bug-free\",\"versionId\":" + id + "}");
        });

        // POST /jumpBugFree
        server.createContext("/jumpBugFree", ex -> {
            addCors(ex);
            if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) { ok(ex, ""); return; }
            if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) { methodNotAllowed(ex); return; }

            VersionNode n = history.lastBugFreeFromCurrent();
            if (n == null) { bad(ex, "No bug-free version found"); return; }

            history.setCurrent(n);
            ok(ex, toJsonCurrent(history.getCurrent()));
        });

        // GET /analytics
        server.createContext("/analytics", ex -> {
            addCors(ex);
            if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) { ok(ex, ""); return; }
            if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) { methodNotAllowed(ex); return; }

            ok(ex, toJsonAnalytics(
                    errorTree.mostFrequent(),
                    errorTree.inorderPairs()
            ));
        });

        // POST /export
        server.createContext("/export", ex -> {
            addCors(ex);
            if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) { ok(ex, ""); return; }
            if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) { methodNotAllowed(ex); return; }

            try {
                exportToFile();
                ok(ex, "{\"message\":\"exported to timeline.json\"}");
            } catch (Exception e) {
                bad(ex, "Export failed: " + e.getMessage());
            }
        });

        // POST /import
        server.createContext("/import", ex -> {
            addCors(ex);
            if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) { ok(ex, ""); return; }
            if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) { methodNotAllowed(ex); return; }

            try {
                importFromFile();
                ok(ex, "{\"message\":\"imported from timeline.json\"}");
            } catch (Exception e) {
                bad(ex, "Import failed: " + e.getMessage());
            }
        });

        server.start();
        System.out.println("API running at http://localhost:" + port);
    }

    // ================== EXPORT / IMPORT JSON ==================

    private static final String FILE_NAME = "timeline.json";

    private static void exportToFile() throws IOException {
        List<Version> list = history.toList();
        Version cur = history.getCurrent();

        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"nextId\":").append(nextId).append(",");
        sb.append("\"currentId\":").append(cur == null ? 0 : cur.versionId).append(",");
        sb.append("\"items\":[");

        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(toJsonVersion(list.get(i)));
        }

        sb.append("]}");

        try (FileWriter fw = new FileWriter(FILE_NAME)) {
            fw.write(sb.toString());
        }
    }

    private static void importFromFile() throws IOException {
        File f = new File(FILE_NAME);
        if (!f.exists()) throw new FileNotFoundException("timeline.json not found. Export first.");

        String json;
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            json = sb.toString();
        }

        history.clearAll();
        undo.clear();
        errorTree.clear();

        int savedNextId = (int) getJsonNumberLoose(json, "nextId");
        int currentId   = (int) getJsonNumberLoose(json, "currentId");

        String itemsArray = getJsonArrayLoose(json, "items");
        List<String> objs = splitJsonObjects(itemsArray);

        for (String obj : objs) {
            int vid = (int) getJsonNumberLoose(obj, "versionId");
            long ts = (long) getJsonNumberLoose(obj, "timestamp");
            String note = getJsonString(obj, "note");
            String errorType = getJsonString(obj, "errorType");
            boolean bugFree = "true".equalsIgnoreCase(getJsonBooleanLoose(obj, "bugFree"));
            String codeText = getJsonString(obj, "codeText");

            Version v = new Version(
                    vid,
                    ts,
                    codeText == null ? "" : codeText,
                    note == null ? "" : note,
                    errorType == null ? "Unknown" : errorType
            );
            v.bugFree = bugFree;

            VersionNode node = history.add(v);
            undo.push(node);
            errorTree.addError(v.errorType);
        }

        if (currentId != 0) {
            VersionNode curNode = history.findById(currentId);
            if (curNode != null) history.setCurrent(curNode);
        }

        nextId = savedNextId > 1 ? savedNextId : (objs.size() + 1);
    }

    // --------- super simple JSON helpers ---------

    private static double getJsonNumberLoose(String json, String key) {
        String k = "\"" + key + "\"";
        int i = json.indexOf(k);
        if (i < 0) return 0;
        int c = json.indexOf(":", i);
        int end = json.indexOf(",", c + 1);
        if (end < 0) end = json.indexOf("}", c + 1);
        return Double.parseDouble(json.substring(c + 1, end).trim());
    }

    private static String getJsonArrayLoose(String json, String key) {
        String k = "\"" + key + "\"";
        int i = json.indexOf(k);
        if (i < 0) return "[]";
        int c = json.indexOf(":", i);
        int s = json.indexOf("[", c);
        int e = json.lastIndexOf("]");
        return json.substring(s, e + 1);
    }

    private static String getJsonBooleanLoose(String json, String key) {
        String k = "\"" + key + "\"";
        int i = json.indexOf(k);
        if (i < 0) return "false";
        int c = json.indexOf(":", i);
        int end = json.indexOf(",", c + 1);
        if (end < 0) end = json.indexOf("}", c + 1);
        return json.substring(c + 1, end).trim();
    }

    private static List<String> splitJsonObjects(String arrayJson) {
        List<String> out = new ArrayList<>();
        String s = arrayJson.trim();
        if (!s.startsWith("[")) return out;

        int depth = 0, start = -1;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '{') {
                if (depth == 0) start = i;
                depth++;
            } else if (ch == '}') {
                depth--;
                if (depth == 0 && start >= 0) {
                    out.add(s.substring(start, i + 1));
                    start = -1;
                }
            }
        }
        return out;
    }

    // ---------- JSON + HTTP helpers (existing) ----------

    private static String toJsonTimeline(List<Version> list, Version current) { /* unchanged */ return ""; }
    private static String toJsonCurrent(Version v) { return ""; }
    private static String toJsonAnalytics(String top, List<String> pairs) { return ""; }
    private static String toJsonVersion(Version v) { return ""; }
    private static String getJsonString(String json, String key) { return null; }
    private static String queryParam(String query, String key) { return null; }
    private static String esc(String s) { return ""; }
    private static void addCors(HttpExchange ex) {}
    private static String readBody(HttpExchange ex) throws IOException { return ""; }
    private static void ok(HttpExchange ex, String body) throws IOException {}
    private static void bad(HttpExchange ex, String msg) throws IOException {}
    private static void methodNotAllowed(HttpExchange ex) throws IOException {}
    private static void write(HttpExchange ex, int code, String body) throws IOException {}
}
