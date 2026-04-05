package quotes;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONObject;

/**
 * Glavni servis: GET /quotes (HTML sa formom, citatom dana, listom), POST /save-quote (čuva i redirektuje na /quotes).
 * Citat dana: HTTP zahtev ka pomoćnom servisu isključivo preko Socket-a (bez HTTP klijent biblioteka).
 */
public final class MainServer {

    private static final int PORT = 8080;
    private static final int AUX_PORT = 8081;
    private static final String AUX_HOST = "localhost";
    private static final String AUX_PATH = "/qod";

    private static final String PAGE_CSS =
            """
            * { box-sizing: border-box; }
            body { margin: 0; font-family: ui-sans-serif, system-ui, sans-serif; background: #eef1f6; color: #111827; line-height: 1.5; }
            .wrap { max-width: 38rem; margin: 0 auto; padding: 1.5rem 1rem 3rem; }
            header h1 { font-size: 1.35rem; font-weight: 700; margin: 0 0 0.2rem; }
            .subtitle { margin: 0; color: #6b7280; font-size: 0.9rem; }
            .hint { font-size: 0.8rem; color: #4b5563; background: #fff; border: 1px solid #e5e7eb; border-radius: 8px; padding: 0.65rem 0.85rem; margin: 1rem 0; }
            .hint code { font-size: 0.88em; background: #f3f4f6; padding: 0.12rem 0.4rem; border-radius: 4px; }
            .card { background: #fff; border: 1px solid #e5e7eb; border-radius: 12px; padding: 1.25rem 1.35rem; margin-bottom: 1rem; box-shadow: 0 1px 2px rgba(0,0,0,.05); }
            .qod blockquote { margin: 0.45rem 0 0.35rem; font-size: 1.05rem; border-left: 3px solid #2563eb; padding-left: 1rem; }
            .qod cite { display: block; font-style: normal; color: #6b7280; font-size: 0.9rem; margin-top: 0.35rem; }
            .badge { display: inline-block; font-size: 0.68rem; font-weight: 700; text-transform: uppercase; letter-spacing: .05em; color: #2563eb; }
            .warn { border-color: #fbbf24; background: #fffbeb; }
            .field { display: block; margin-bottom: 1rem; }
            .field span { display: block; font-size: 0.85rem; font-weight: 600; margin-bottom: 0.35rem; }
            textarea, input[type=text] { width: 100%; padding: 0.55rem 0.65rem; border: 1px solid #d1d5db; border-radius: 8px; font: inherit; }
            textarea { min-height: 6.5rem; resize: vertical; }
            textarea:focus, input:focus { outline: 2px solid #93c5fd; border-color: #2563eb; }
            button { background: #2563eb; color: #fff; border: none; padding: 0.55rem 1.15rem; border-radius: 8px; font-weight: 600; cursor: pointer; font-size: 0.95rem; }
            button:hover { background: #1d4ed8; }
            h2 { font-size: 1.05rem; margin: 0 0 1rem; }
            ul { margin: 0; padding-left: 1.15rem; }
            li { margin-bottom: 0.55rem; }
            .muted { color: #6b7280; margin: 0; font-size: 0.9rem; }
            """;

    private static final List<Quote> STORED = Collections.synchronizedList(new ArrayList<>());

    public static void main(String[] args) throws IOException {
        try (ServerSocket server = new ServerSocket(PORT)) {
            logMain("slušam na portu " + PORT + " — http://localhost:" + PORT + "/quotes");
            while (true) {
                try (Socket client = server.accept()) {
                    handle(client);
                }
            }
        }
    }

    private static void logMain(String msg) {
        System.out.println(LocalTime.now() + " [glavni] " + msg);
    }

    private static String ellipsize(String s, int maxChars) {
        if (s == null) {
            return "";
        }
        String t = s.replace('\n', ' ').trim();
        return t.length() <= maxChars ? t : t.substring(0, maxChars) + "…";
    }

    private static void handle(Socket client) throws IOException {
        String peer = String.valueOf(client.getRemoteSocketAddress());
        InputStream in = client.getInputStream();
        String requestLine = readHttpLine(in);
        if (requestLine == null || requestLine.isEmpty()) {
            logMain(peer + " → (prazan zahtev, ignorišem)");
            return;
        }
        logMain(peer + " → " + requestLine);
        Map<String, String> headers = new LinkedHashMap<>();
        while (true) {
            String h = readHttpLine(in);
            if (h == null || h.isEmpty()) {
                break;
            }
            int c = h.indexOf(':');
            if (c > 0) {
                headers.put(h.substring(0, c).trim().toLowerCase(), h.substring(c + 1).trim());
            }
        }

        String[] parts = requestLine.split(" ");
        if (parts.length < 2) {
            logMain("odgovor 400 Bad Request (neispravna prva linija)");
            sendText(client.getOutputStream(), 400, "Bad Request");
            return;
        }
        String method = parts[0].toUpperCase();
        String target = parts[1];
        if (target.contains("?")) {
            target = target.substring(0, target.indexOf('?'));
        }

        if ("GET".equals(method) && "/quotes".equals(target)) {
            String html = buildQuotesPage();
            int n;
            synchronized (STORED) {
                n = STORED.size();
            }
            logMain("GET /quotes → 200 OK (HTML " + html.length() + " B, sačuvanih citata: " + n + ")");
            sendHtml(client.getOutputStream(), 200, html);
            return;
        }

        if ("POST".equals(method) && "/save-quote".equals(target)) {
            int len = parseContentLength(headers);
            if (len < 0 || len > 1_000_000) {
                logMain("POST /save-quote → 400 (Content-Length: " + len + ")");
                sendText(client.getOutputStream(), 400, "Bad Content-Length");
                return;
            }
            logMain("POST /save-quote, telo " + len + " B");
            byte[] buf = new byte[len];
            int read = 0;
            while (read < len) {
                int n = in.read(buf, read, len - read);
                if (n < 0) {
                    break;
                }
                read += n;
            }
            String body = new String(buf, 0, read, StandardCharsets.UTF_8);
            Map<String, String> form = parseForm(body);
            String text = form.getOrDefault("text", "").trim();
            String author = form.getOrDefault("author", "").trim();
            if (!text.isEmpty()) {
                STORED.add(new Quote(text, author));
                logMain(
                        "sačuvan citat: \""
                                + ellipsize(text, 72)
                                + "\" | autor: "
                                + ellipsize(author, 40)
                                + " | ukupno u memoriji: "
                                + STORED.size());
            } else {
                logMain("POST /save-quote: prazan citat — ništa nije sačuvano");
            }
            logMain("odgovor 302 → /quotes");
            redirect(client.getOutputStream(), "/quotes");
            return;
        }

        logMain(method + " " + target + " → 404 Not Found");
        sendText(client.getOutputStream(), 404, "Not Found");
    }

    /** Čita jednu HTTP liniju (do LF); isti stream koristi i za telo POST-a (bez BufferedReader lookahead). */
    private static String readHttpLine(InputStream in) throws IOException {
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        while (true) {
            int b = in.read();
            if (b == -1) {
                break;
            }
            if (b == '\n') {
                break;
            }
            line.write(b);
        }
        String s = line.toString(StandardCharsets.ISO_8859_1);
        if (s.endsWith("\r")) {
            s = s.substring(0, s.length() - 1);
        }
        return s;
    }

    private static int parseContentLength(Map<String, String> headers) {
        String v = headers.get("content-length");
        if (v == null) {
            return -1;
        }
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static Map<String, String> parseForm(String body) {
        Map<String, String> m = new LinkedHashMap<>();
        if (body == null || body.isEmpty()) {
            return m;
        }
        for (String pair : body.split("&")) {
            int eq = pair.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            try {
                String k = URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8);
                String v = URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
                m.put(k, v);
            } catch (Exception ignored) {
                // preskoči neispravan par
            }
        }
        return m;
    }

    /** Zahtev ka pomoćnom servisu: isključivo Socket + ručni HTTP (bez HTTP klijent biblioteka). */
    private static String quoteOfDayBlock() {
        try (Socket s = new Socket(AUX_HOST, AUX_PORT)) {
            logMain("pomoćni: šaljem GET " + AUX_HOST + ":" + AUX_PORT + AUX_PATH + " (Socket)");
            OutputStream os = s.getOutputStream();
            String req =
                    "GET "
                            + AUX_PATH
                            + " HTTP/1.1\r\nHost: "
                            + AUX_HOST
                            + ":"
                            + AUX_PORT
                            + "\r\nConnection: close\r\n\r\n";
            os.write(req.getBytes(StandardCharsets.US_ASCII));
            os.flush();

            ByteArrayOutputStream acc = new ByteArrayOutputStream();
            byte[] buf = new byte[1024];
            InputStream is = s.getInputStream();
            int n;
            while ((n = is.read(buf)) != -1) {
                acc.write(buf, 0, n);
            }
            String full = acc.toString(StandardCharsets.UTF_8);
            int sep = full.indexOf("\r\n\r\n");
            if (sep < 0) {
                logMain("pomoćni: odgovor bez razdvajanja zaglavlja/tela");
                return "<div class=\"card qod warn\"><p class=\"muted\">Citat dana trenutno nije dostupan.</p></div>";
            }
            String body = full.substring(sep + 4).trim();
            JSONObject json = new JSONObject(body);
            String rawQ = json.optString("quote", "");
            String rawA = json.optString("author", "");
            logMain("pomoćni: primljen JSON — \"" + ellipsize(rawQ, 56) + "\" — " + rawA);
            String q = escapeHtml(rawQ);
            String a = escapeHtml(rawA);
            return "<div class=\"card qod\"><span class=\"badge\">Citat dana</span><blockquote>"
                    + q
                    + "</blockquote><cite>— "
                    + a
                    + "</cite></div>";
        } catch (Exception e) {
            logMain("pomoćni: greška pri pozivu — " + e.getClass().getSimpleName() + ": " + e.getMessage());
            return "<div class=\"card qod warn\"><p class=\"muted\">Citat dana nije dostupan — pokreni pomoćni servis na portu "
                    + AUX_PORT
                    + ".</p></div>";
        }
    }

    private static String buildQuotesPage() {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html lang=\"sr\"><head><meta charset=\"UTF-8\">");
        sb.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">");
        sb.append("<title>Quotes — test</title><style>");
        sb.append(PAGE_CSS);
        sb.append("</style></head><body><main class=\"wrap\">");
        sb.append("<header><h1>Quotes</h1><p class=\"subtitle\">Unos i pregled citata (domaći backend)</p></header>");
        sb.append("<p class=\"hint\"><strong>Test:</strong> glavni servis <code>http://localhost:")
                .append(PORT)
                .append("/quotes</code> · pomoćni (JSON) <code>localhost:")
                .append(AUX_PORT)
                .append(AUX_PATH)
                .append("</code></p>");
        sb.append(quoteOfDayBlock());
        sb.append("<section class=\"card\"><h2>Novi citat</h2>");
        sb.append("<form method=\"POST\" action=\"/save-quote\" accept-charset=\"UTF-8\">");
        sb.append("<label class=\"field\"><span>Tekst citata</span>");
        sb.append("<textarea name=\"text\" rows=\"5\" required placeholder=\"Unesi citat...\"></textarea></label>");
        sb.append("<label class=\"field\"><span>Autor</span>");
        sb.append("<input type=\"text\" name=\"author\" autocomplete=\"off\" placeholder=\"Ime autora\"></label>");
        sb.append("<button type=\"submit\">Save Quote</button></form></section>");
        sb.append("<section class=\"card\"><h2>Sačuvani citati</h2>");
        synchronized (STORED) {
            if (STORED.isEmpty()) {
                sb.append("<p class=\"muted\">Još nema unosa. Pošalji formu iznad.</p>");
            } else {
                sb.append("<ul>");
                for (int i = STORED.size() - 1; i >= 0; i--) {
                    Quote q = STORED.get(i);
                    sb.append("<li>")
                            .append(escapeHtml(q.text))
                            .append(" <span class=\"muted\">— ")
                            .append(escapeHtml(q.author))
                            .append("</span></li>");
                }
                sb.append("</ul>");
            }
        }
        sb.append("</section></main></body></html>");
        return sb.toString();
    }

    private static String escapeHtml(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private static void sendHtml(OutputStream out, int status, String html) throws IOException {
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        PrintWriter w = new PrintWriter(new OutputStreamWriter(out, StandardCharsets.ISO_8859_1));
        w.print("HTTP/1.1 " + status + " OK\r\n");
        w.print("Content-Type: text/html; charset=utf-8\r\n");
        w.print("Content-Length: " + bytes.length + "\r\n");
        w.print("Connection: close\r\n");
        w.print("\r\n");
        w.flush();
        out.write(bytes);
        out.flush();
    }

    private static void sendText(OutputStream out, int status, String msg) throws IOException {
        byte[] bytes = msg.getBytes(StandardCharsets.UTF_8);
        PrintWriter w = new PrintWriter(new OutputStreamWriter(out, StandardCharsets.ISO_8859_1));
        w.print("HTTP/1.1 " + status + " ERR\r\n");
        w.print("Content-Type: text/plain; charset=utf-8\r\n");
        w.print("Content-Length: " + bytes.length + "\r\n");
        w.print("Connection: close\r\n");
        w.print("\r\n");
        w.flush();
        out.write(bytes);
        out.flush();
    }

    private static void redirect(OutputStream out, String location) throws IOException {
        PrintWriter w = new PrintWriter(new OutputStreamWriter(out, StandardCharsets.ISO_8859_1));
        w.print("HTTP/1.1 302 Found\r\n");
        w.print("Location: " + location + "\r\n");
        w.print("Content-Length: 0\r\n");
        w.print("Connection: close\r\n");
        w.print("\r\n");
        w.flush();
    }

    private record Quote(String text, String author) {}
}
