import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class SimpleServer {

    // "База данных" для тестов
    static Map<Integer, String> items = new ConcurrentHashMap<>();
    static AtomicInteger idSeq = new AtomicInteger(1);

    public static void main(String[] args) throws Exception {

        int port = 8080;
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        // ==========================
        // HEALTH
        // ==========================
        server.createContext("/api/health", ex -> {
            sendJson(ex, """
                { "status": "UP" }
            """);
        });

        // ==========================
        // RESET (очень важно для AT)
        // ==========================
        server.createContext("/api/test/reset", ex -> {
            items.clear();
            idSeq.set(1);

            sendJson(ex, """
                { "status": "RESET_DONE" }
            """);
        });

        // ==========================
        // CREATE ITEM
        // POST /api/items?name=abc
        // ==========================
        server.createContext("/api/items", ex -> {

            String method = ex.getRequestMethod();

            if ("POST".equalsIgnoreCase(method)) {

                String query = ex.getRequestURI().getQuery();
                String name = getQueryParam(query, "name");

                int id = idSeq.getAndIncrement();
                items.put(id, name);

                sendJson(ex, """
                    {
                      "id": %d,
                      "name": "%s",
                      "status": "CREATED"
                    }
                """.formatted(id, name));

                return;
            }

            if ("GET".equalsIgnoreCase(method)) {

                StringBuilder sb = new StringBuilder();
                sb.append("{\"items\":[");

                boolean first = true;
                for (var e : items.entrySet()) {
                    if (!first) sb.append(",");
                    first = false;

                    sb.append("""
                        {"id":%d,"name":"%s"}
                    """.formatted(e.getKey(), e.getValue()));
                }

                sb.append("]}");

                sendJson(ex, sb.toString());
                return;
            }

            methodNotAllowed(ex);
        });

        // ==========================
        // GET BY ID
        // ==========================
        server.createContext("/api/items/get", ex -> {

            String query = ex.getRequestURI().getQuery();
            int id = Integer.parseInt(getQueryParam(query, "id"));

            String name = items.get(id);

            if (name == null) {
                notFound(ex);
                return;
            }

            sendJson(ex, """
                {
                  "id": %d,
                  "name": "%s"
                }
            """.formatted(id, name));
        });

        // ==========================
        // DELETE
        // DELETE /api/items?id=1
        // ==========================
        server.createContext("/api/items/delete", ex -> {

            if (!"DELETE".equalsIgnoreCase(ex.getRequestMethod())) {
                methodNotAllowed(ex);
                return;
            }

            String query = ex.getRequestURI().getQuery();
            int id = Integer.parseInt(getQueryParam(query, "id"));

            String removed = items.remove(id);

            if (removed == null) {
                notFound(ex);
                return;
            }

            sendJson(ex, """
                {
                  "status": "DELETED",
                  "id": %d
                }
            """.formatted(id));
        });

        server.setExecutor(null);
        server.start();

        System.out.println("Server started on 8080");

        server.createContext("/", ex -> {

            String html = """
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8" />
<title>Neon Items</title>

<style>
body {
    margin: 0;
    font-family: Arial;
    background: linear-gradient(-45deg, #0f0c29, #302b63, #24243e, #0f0c29);
    background-size: 400% 400%;
    animation: bg 10s ease infinite;
    color: white;
    overflow-x: hidden;
}

@keyframes bg {
    0% {background-position:0% 50%;}
    50% {background-position:100% 50%;}
    100% {background-position:0% 50%;}
}

h1 {
    text-align: center;
    padding: 20px;
    font-size: 32px;
    text-shadow: 0 0 10px cyan;
}

.container {
    max-width: 600px;
    margin: auto;
}

.card {
    background: rgba(255,255,255,0.08);
    margin: 10px;
    padding: 15px;
    border-radius: 12px;
    backdrop-filter: blur(10px);
    border: 1px solid rgba(255,255,255,0.1);
    transition: 0.3s;
    animation: pop 0.3s ease;
}

.card:hover {
    transform: scale(1.03);
    box-shadow: 0 0 20px cyan;
}

@keyframes pop {
    from { transform: scale(0.8); opacity: 0; }
    to { transform: scale(1); opacity: 1; }
}

button {
    background: cyan;
    border: none;
    padding: 10px;
    margin: 10px;
    border-radius: 8px;
    cursor: pointer;
}

input {
    padding: 10px;
    border-radius: 8px;
    border: none;
}
</style>
</head>

<body>

<h1>⚡ Neeon Item Tracker</h1>

<div class="container">
    <input id="name" placeholder="new item..." />
    <button onclick="addItem()">Add</button>

    <div id="list"></div>
</div>

<script>

async function load() {
    const res = await fetch('/api/items');
    const data = await res.json();

    const list = document.getElementById('list');
    list.innerHTML = '';

    for (const item of data.items) {
        const div = document.createElement('div');
        div.className = 'card';
        div.innerText = `#${item.id} — ${item.name}`;
        list.appendChild(div);
    }
}

async function addItem() {
    const name = document.getElementById('name').value;

    await fetch('/api/items?name=' + encodeURIComponent(name), {
        method: 'POST'
    });

    document.getElementById('name').value = '';
    load();
}

load();
setInterval(load, 2000);

</script>

</body>
</html>
""";

            byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
            ex.sendResponseHeaders(200, bytes.length);
            ex.getResponseBody().write(bytes);
            ex.close();
        });
    }

    // ==========================
    // helpers
    // ==========================

    static String getQueryParam(String query, String key) {
        if (query == null) return null;
        for (String part : query.split("&")) {
            String[] kv = part.split("=");
            if (kv.length == 2 && kv[0].equals(key)) {
                return kv[1];
            }
        }
        return null;
    }

    static void sendJson(HttpExchange ex, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);

        ex.getResponseHeaders().add("Content-Type", "application/json");
        ex.sendResponseHeaders(200, bytes.length);

        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    static void methodNotAllowed(HttpExchange ex) throws IOException {
        ex.sendResponseHeaders(405, -1);
        ex.close();
    }

    static void notFound(HttpExchange ex) throws IOException {
        ex.sendResponseHeaders(404, -1);
        ex.close();
    }
}