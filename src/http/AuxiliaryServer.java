package http;

import com.google.gson.Gson;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Pomoćni servis: jedan endpoint vraća nasumično odabran "citat dana" u JSON formatu.
 * Sluša na localhost:8081, putanja: GET /qod
 */
public class AuxiliaryServer {

    private static final int PORT = 8081;
    private static final String PATH = "/qod";

    private static final Path QUOTES_POOL_FILE = Path.of("data", "quotes.jsonl");

    private static final String[][] POOL = {
            {"Budi promena koju želiš da vidiš u svetu.", "Mahatma Gandhi"},
            {"Jedini način da uradite sjajan posao je da volite ono što radite.", "Steve Jobs"},
            {"Ne čekaj; pravo vreme nikada neće doći.", "Napoleon Hill"},
            {"Jednostavnost je vrhunac sofisticiranosti.", "Leonardo da Vinci"},
            {"Znanje je moć.", "Francis Bacon"}
    };

    private static final Gson GSON = new Gson();

    public static void main(String[] args) {
        Random rnd = new Random();
        try (ServerSocket ss = new ServerSocket(PORT)) {
            log("slušam na portu " + PORT + " — http://localhost:" + PORT + PATH);
            while (true) {
                Socket client = ss.accept();
                new Thread(() -> handle(client, rnd)).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void handle(Socket client, Random rnd) {
        try (
                Socket c = client;
                BufferedReader in = new BufferedReader(new InputStreamReader(c.getInputStream(), StandardCharsets.ISO_8859_1));
                OutputStream out = c.getOutputStream()
        ) {
            String requestLine = in.readLine();
            if (requestLine == null || requestLine.isEmpty()) {
                return;
            }

            String[] parts = requestLine.split(" ");
            String method = parts.length > 0 ? parts[0] : "";
            String path = parts.length > 1 ? parts[1] : "";
            if (path.contains("?")) {
                path = path.substring(0, path.indexOf('?'));
            }

            // read headers (not strictly needed, but consumes input)
            while (true) {
                String h = in.readLine();
                if (h == null || h.trim().isEmpty()) {
                    break;
                }
            }

            if (!"GET".equalsIgnoreCase(method)) {
                send(out, 405, "Method Not Allowed", "text/plain; charset=utf-8", "Method Not Allowed");
                return;
            }
            if (!PATH.equals(path)) {
                send(out, 404, "Not Found", "text/plain; charset=utf-8", "Not Found");
                return;
            }

            Quote pick = pickQuoteOfDay(rnd);
            Map<String, String> payload = new LinkedHashMap<>();
            payload.put("quote", pick.quote);
            payload.put("author", pick.author);
            String json = GSON.toJson(payload);
            send(out, 200, "OK", "application/json; charset=utf-8", json);
        } catch (IOException e) {
            // ignore client errors
        }
    }

    private static Quote pickQuoteOfDay(Random rnd) {
        // First, try dynamic pool created from user-submitted quotes.
        List<Quote> dynamic = readDynamicPool();
        if (!dynamic.isEmpty()) {
            return dynamic.get(rnd.nextInt(dynamic.size()));
        }
        // Fallback to built-in pool.
        int i = rnd.nextInt(POOL.length);
        return new Quote(POOL[i][0], POOL[i][1]);
    }

    private static List<Quote> readDynamicPool() {
        try {
            if (!Files.exists(QUOTES_POOL_FILE)) {
                return List.of();
            }
            List<String> lines = Files.readAllLines(QUOTES_POOL_FILE, StandardCharsets.UTF_8);
            List<Quote> quotes = new ArrayList<>();
            for (String line : lines) {
                String t = line == null ? "" : line.trim();
                if (t.isEmpty()) {
                    continue;
                }
                try {
                    Map<?, ?> obj = GSON.fromJson(t, Map.class);
                    if (obj == null) {
                        continue;
                    }
                    Object q = obj.get("quote");
                    Object a = obj.get("author");
                    String quote = q == null ? "" : String.valueOf(q);
                    String author = a == null ? "" : String.valueOf(a);
                    if (!quote.trim().isEmpty()) {
                        quotes.add(new Quote(quote, author));
                    }
                } catch (Exception ignored) {
                    // skip malformed line
                }
            }
            return quotes;
        } catch (IOException e) {
            return List.of();
        }
    }

    private static class Quote {
        private final String quote;
        private final String author;

        private Quote(String quote, String author) {
            this.quote = quote;
            this.author = author;
        }
    }

    private static void send(OutputStream out, int status, String reason, String contentType, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        String headers =
                "HTTP/1.1 " + status + " " + reason + "\r\n"
                        + "Content-Type: " + contentType + "\r\n"
                        + "Content-Length: " + bytes.length + "\r\n"
                        + "Connection: close\r\n"
                        + "\r\n";
        out.write(headers.getBytes(StandardCharsets.ISO_8859_1));
        out.write(bytes);
        out.flush();
    }

    private static void log(String msg) {
        System.out.println(LocalTime.now() + " [aux] " + msg);
    }
}

