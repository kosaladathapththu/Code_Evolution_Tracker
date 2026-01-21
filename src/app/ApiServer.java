package app;

import com.sun.net.httpserver.*;
import ds.*;
import model.Version;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class ApiServer {

    private static final VersionLinkedList history = new VersionLinkedList();
    private static final UndoStack undo = new UndoStack();
    private static final ErrorBST errorTree = new ErrorBST();
    private static int nextId = 1;

    public static void main(String[] args) throws Exception {
        int port = 8080;
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        // POST /step  (save a debugging step)
        server.createContext("/step", ex -> {
            addCors(ex);
            if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) { ok(ex, ""); return; }
            if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) { methodNotAllowed(ex); return; }

            String body = readBody(ex);
            String code = getJsonString(body, "codeText");
            String note = getJsonString(body, "note");
            String errorType = getJsonString(body, "errorType");

            if (code == null || code.trim().isEmpty()) { bad(ex, "codeText required"); return; }

            Version v = new Version(nextId++, System.currentTimeMillis(), code, note == null ? "" : note, errorType == null ? "Unknown" : errorType);
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

            // pop current, then set current to previous if exists
            VersionNode cur = undo.pop();
            if (cur == null || cur.prev == null) { bad(ex, "Nothing to undo"); return; }
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

        // POST /jumpBugFree (go to last bug-free from current)
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

            String top = errorTree.mostFrequent();
            List<String> pairs = errorTree.inorderPairs();
            ok(ex, toJsonAnalytics(top, pairs));
        });

        server.start();
        System.out.println("API running at http://localhost:" + port);
    }

    // ---------- JSON helpers ----------
    private static String toJsonTimeline(List<Version> list, Version current) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"current\":").append(current == null ? "null" : toJsonVersion(current)).append(",\"timeline\":[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(toJsonVersion(list.get(i)));
        }
        sb.append("]}");
        return sb.toString();
    }

    private static String toJsonCurrent(Version v) {
        return "{\"current\":" + (v == null ? "null" : toJsonVersion(v)) + "}";
    }

    private static String toJsonAnalytics(String top, List<String> pairs) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"mostFrequent\":").append(top == null ? "null" : "\"" + esc(top) + "\"").append(",\"sortedCounts\":[");
        for (int i = 0; i < pairs.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(esc(pairs.get(i))).append("\"");
        }
        sb.append("]}");
        return sb.toString();
    }

    private static String toJsonVersion(Version v) {
        return "{"
                + "\"versionId\":" + v.versionId + ","
                + "\"timestamp\":" + v.timestamp + ","
                + "\"note\":\"" + esc(v.note) + "\","
                + "\"errorType\":\"" + esc(v.errorType) + "\","
                + "\"bugFree\":" + v.bugFree + ","
                + "\"codeText\":\"" + esc(v.codeText) + "\""
                + "}";
    }

    private static String getJsonString(String json, String key) {
        if (json == null) return null;
        String k = "\"" + key + "\"";
        int i = json.indexOf(k);
        if (i < 0) return null;
        int c = json.indexOf(":", i);
        int q1 = json.indexOf("\"", c + 1);
        int q2 = json.indexOf("\"", q1 + 1);
        if (q1 < 0 || q2 < 0) return null;
        return json.substring(q1 + 1, q2);
    }

    private static String queryParam(String query, String key) {
        if (query == null) return null;
        for (String part : query.split("&")) {
            String[] kv = part.split("=", 2);
            if (kv.length == 2 && kv[0].equals(key)) return kv[1];
        }
        return null;
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "");
    }

    // ---------- HTTP helpers ----------
    private static void addCors(HttpExchange ex) {
        ex.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        ex.getResponseHeaders().add("Access-Control-Allow-Methods", "GET,POST,OPTIONS");
        ex.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");
    }

    private static String readBody(HttpExchange ex) throws IOException {
        try (InputStream is = ex.getRequestBody()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static void ok(HttpExchange ex, String body) throws IOException { write(ex, 200, body); }
    private static void bad(HttpExchange ex, String msg) throws IOException { write(ex, 400, "{\"error\":\"" + esc(msg) + "\"}"); }
    private static void methodNotAllowed(HttpExchange ex) throws IOException { write(ex, 405, "{\"error\":\"Method not allowed\"}"); }

    private static void write(HttpExchange ex, int code, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
    }
}
