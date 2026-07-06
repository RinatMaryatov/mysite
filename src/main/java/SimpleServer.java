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
            sendHtml(exchange, htmlPage(counter.get()));
        });

        server.createContext("/click", exchange -> {

            counter.incrementAndGet();

            exchange.getResponseHeaders().add("Location", "/");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });

        // ==========================
        // REST API
        // ==========================

        // GET /api/counter
        server.createContext("/api/counter", exchange -> {

            if (!exchange.getRequestMethod().equalsIgnoreCase("GET")) {
                methodNotAllowed(exchange);
                return;
            }

            String json = """
                    {
                      "count": %d
                    }
                    """.formatted(counter.get());

            sendJson(exchange, json);
        });

        // POST /api/counter/increment
        server.createContext("/api/counter/increment", exchange -> {

            if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                methodNotAllowed(exchange);
                return;
            }

            int value = counter.incrementAndGet();

            String json = """
                    {
                      "message":"Counter incremented",
                      "count": %d
                    }
                    """.formatted(value);

            sendJson(exchange, json);
        });

        // POST /api/counter/reset
        server.createContext("/api/counter/reset", exchange -> {

            if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                methodNotAllowed(exchange);
                return;
            }

            counter.set(0);

            String json = """
                    {
                      "message":"Counter reset",
                      "count":0
                    }
                    """;

            sendJson(exchange, json);
        });

        // GET /api/health
        server.createContext("/api/health", exchange -> {

            if (!exchange.getRequestMethod().equalsIgnoreCase("GET")) {
                methodNotAllowed(exchange);
                return;
            }

            String json = """
                    {
                      "status":"UP"
                    }
                    """;

            sendJson(exchange, json);
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

    static String htmlPage(int count) {

        return """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="UTF-8">
                    <title>Simple Java Site</title>
                </head>
                <body style="font-family:sans-serif;text-align:center;margin-top:80px;">

                    <h1>Ультрапростой сайт на Java</h1>

                    <p>Счётчик: <span id="count">%d</span></p>

                    <button onclick="clickMe()">+1</button>

                    <script>
                        async function clickMe() {
                            await fetch('/click');
                            location.reload();
                        }
                    </script>

                </body>
                </html>
                """.formatted(count);
    }
}