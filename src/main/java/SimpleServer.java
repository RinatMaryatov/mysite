import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicInteger;

public class SimpleServer {

    static AtomicInteger counter = new AtomicInteger(0);

    public static void main(String[] args) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);

        // главная страница
        server.createContext("/", exchange -> {
            String response = htmlPage(counter.get());
            send(exchange, response);
        });

        // "логика" — увеличение счётчика
        server.createContext("/click", exchange -> {
            counter.incrementAndGet();
            String response = "OK";
            send(exchange, response);
        });

        server.setExecutor(null);
        server.start();

        System.out.println("Server started: http://localhost:8080");
    }

    static void send(HttpExchange exchange, String response) throws IOException {
        exchange.getResponseHeaders().add("Content-Type", "text/html; charset=UTF-8");
        exchange.sendResponseHeaders(200, response.getBytes().length);

        OutputStream os = exchange.getResponseBody();
        os.write(response.getBytes());
        os.close();
    }

    static String htmlPage(int count) {
        return """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="UTF-8">
            <title>Simple Java Page</title>
        </head>
        <body style="font-family: sans-serif; text-align:center; margin-top:100px;">

            <h1>Ультрапростой сайт на Java</h1>

            <p>Счётчик: <span id="count">%d</span></p>

            <button onclick="clickMe()">Нажми меня</button>

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