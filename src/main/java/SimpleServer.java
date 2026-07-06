import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

public class SimpleServer {

    static AtomicInteger counter = new AtomicInteger(0);

    public static void main(String[] args) throws Exception {

        String portEnv = System.getenv("PORT");
        int port = portEnv != null ? Integer.parseInt(portEnv) : 8080;

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        // ==========================
        // UI
        // ==========================

        server.createContext("/", exchange -> {
            sendHtml(exchange, htmlPage());
        });

        // ==========================
        // REST API
        // ==========================

        server.createContext("/api/counter", exchange -> {
            if (!exchange.getRequestMethod().equalsIgnoreCase("GET")) {
                methodNotAllowed(exchange);
                return;
            }

            sendJson(exchange, """
                    { "count": %d }
                    """.formatted(counter.get()));
        });

        server.createContext("/api/counter/increment", exchange -> {
            if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                methodNotAllowed(exchange);
                return;
            }

            int value = counter.incrementAndGet();

            sendJson(exchange, """
                    {
                      "message": "Counter incremented",
                      "count": %d
                    }
                    """.formatted(value));
        });

        server.createContext("/api/counter/reset", exchange -> {
            if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                methodNotAllowed(exchange);
                return;
            }

            counter.set(0);

            sendJson(exchange, """
                    {
                      "message": "Counter reset",
                      "count": 0
                    }
                    """);
        });

        server.createContext("/api/health", exchange -> {
            if (!exchange.getRequestMethod().equalsIgnoreCase("GET")) {
                methodNotAllowed(exchange);
                return;
            }

            sendJson(exchange, """
                    { "status": "UP" }
                    """);
        });

        server.setExecutor(null);
        server.start();

        System.out.println("Server started on port " + port);
    }

    // ==========================
    // Helpers
    // ==========================

    static void sendHtml(HttpExchange exchange, String html) throws IOException {
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);

        exchange.getResponseHeaders().add("Content-Type", "text/html; charset=UTF-8");
        exchange.sendResponseHeaders(200, bytes.length);

        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    static void sendJson(HttpExchange exchange, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);

        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);

        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    static void methodNotAllowed(HttpExchange exchange) throws IOException {
        exchange.sendResponseHeaders(405, -1);
        exchange.close();
    }

    // ==========================
    // UI
    // ==========================

    static String htmlPage() {
        return """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="UTF-8">
                    <title>Simple Java Site</title>
                </head>

                <body style="font-family:sans-serif;text-align:center;margin-top:60px;">

                    <h1>Ультрапростой сайт на Java</h1>

                    <p>Счётчик: <span id="count">?</span></p>
                    <p>Health: <span id="health">?</span></p>

                    <div style="margin-top:20px;">
                        <button onclick="getCounter()">GET counter</button>
                        <button onclick="increment()">+1</button>
                        <button onclick="resetCounter()">reset</button>
                        <button onclick="health()">health</button>
                    </div>

                    <script>
                        async function getCounter() {
                            const res = await fetch('/api/counter');
                            const data = await res.json();
                            document.getElementById('count').innerText = data.count;
                        }

                        async function increment() {
                            const res = await fetch('/api/counter/increment', { method: 'POST' });
                            const data = await res.json();
                            document.getElementById('count').innerText = data.count;
                        }

                        async function resetCounter() {
                            const res = await fetch('/api/counter/reset', { method: 'POST' });
                            const data = await res.json();
                            document.getElementById('count').innerText = data.count;
                        }

                        async function health() {
                            const res = await fetch('/api/health');
                            const data = await res.json();
                            document.getElementById('health').innerText = data.status;
                        }

                        // initial load
                        getCounter();
                    </script>

                </body>
                </html>
                """;
    }
}