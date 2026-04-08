package app;

import http.Request;
import http.response.HtmlResponse;
import http.response.RedirectResponse;
import http.response.Response;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

public class QuotesController extends Controller {

    private static final int AUX_PORT = 8081;
    private static final String AUX_HOST = "localhost";
    private static final String AUX_PATH = "/qod";

    private static final List<Quote> STORED = Collections.synchronizedList(new ArrayList<>());
    private static final Gson GSON = new Gson();

    public QuotesController(Request request) {
        super(request);
    }

    @Override
    public Response doGet() {
        String qodHtml = quoteOfDayBlock();

        StringBuilder list = new StringBuilder();
        synchronized (STORED) {
            if (STORED.isEmpty()) {
                list.append("<p><i>Još nema unosa.</i></p>");
            } else {
                list.append("<ul>");
                for (int i = STORED.size() - 1; i >= 0; i--) {
                    Quote q = STORED.get(i);
                    list.append("<li>")
                            .append(escapeHtml(q.text))
                            .append(" <span style=\"color:#666\">— ")
                            .append(escapeHtml(q.author))
                            .append("</span></li>");
                }
                list.append("</ul>");
            }
        }

        String htmlBody =
                "<h1>Quotes</h1>"
                        + qodHtml
                        + "<h2>Novi citat</h2>"
                        + "<form method=\"POST\" action=\"/save-quote\">"
                        + "<label>Tekst citata:</label><br>"
                        + "<textarea name=\"text\" rows=\"4\" cols=\"50\" required></textarea><br><br>"
                        + "<label>Autor:</label><br>"
                        + "<input name=\"author\" type=\"text\"><br><br>"
                        + "<button>Save Quote</button>"
                        + "</form>"
                        + "<hr>"
                        + "<h2>Sačuvani citati</h2>"
                        + list;

        String content = "<html><head><meta charset=\"UTF-8\"><title>Quotes</title></head>\n";
        content += "<body>" + htmlBody + "</body></html>";

        return new HtmlResponse(content);
    }

    @Override
    public Response doPost() {
        Map<String, String> form = parseForm(request.getBody());
        String text = form.getOrDefault("text", "").trim();
        String author = form.getOrDefault("author", "").trim();

        if (!text.isEmpty()) {
            STORED.add(new Quote(text, author));
        }

        return new RedirectResponse("/quotes");
    }

    private static Map<String, String> parseForm(String body) {
        Map<String, String> m = new LinkedHashMap<>();
        if (body == null || body.isEmpty()) {
            return m;
        }
        for (String pair : body.split("&")) {
            int eq = pair.indexOf('=');
            if (eq < 0) {
                continue;
            }
            String k = urlDecode(pair.substring(0, eq));
            String v = urlDecode(pair.substring(eq + 1));
            m.put(k, v);
        }
        return m;
    }

    private static String urlDecode(String s) {
        try {
            return URLDecoder.decode(s, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return s;
        }
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

    /**
     * Poziva pomoćni servis preko čistog Socket-a (ručno sastavljen HTTP zahtev).
     * Očekuje JSON telo sa poljima: quote, author.
     */
    private static String quoteOfDayBlock() {
        try (Socket s = new Socket(AUX_HOST, AUX_PORT)) {
            OutputStream os = s.getOutputStream();
            String req =
                    "GET " + AUX_PATH + " HTTP/1.1\r\n"
                            + "Host: " + AUX_HOST + ":" + AUX_PORT + "\r\n"
                            + "Connection: close\r\n"
                            + "\r\n";
            os.write(req.getBytes(StandardCharsets.US_ASCII));
            os.flush();

            ByteArrayOutputStream acc = new ByteArrayOutputStream();
            byte[] buf = new byte[1024];
            int n;
            while ((n = s.getInputStream().read(buf)) != -1) {
                acc.write(buf, 0, n);
            }

            String full = acc.toString(StandardCharsets.UTF_8);
            int sep = full.indexOf("\r\n\r\n");
            if (sep < 0) {
                return "<p style=\"color:#b45309\"><b>Citat dana</b>: nije dostupan.</p>";
            }
            String body = full.substring(sep + 4).trim();

            JsonObject obj = GSON.fromJson(body, JsonObject.class);
            String q = obj != null && obj.has("quote") ? obj.get("quote").getAsString() : "";
            String a = obj != null && obj.has("author") ? obj.get("author").getAsString() : "";

            return "<div style=\"padding:10px;border:1px solid #ddd;border-radius:8px;margin:10px 0\">"
                    + "<div style=\"font-size:12px;color:#2563eb;font-weight:700\">CITAT DANA</div>"
                    + "<blockquote style=\"margin:8px 0 4px\">"
                    + escapeHtml(q)
                    + "</blockquote>"
                    + "<div style=\"color:#666\">— "
                    + escapeHtml(a)
                    + "</div></div>";
        } catch (Exception e) {
            return "<p style=\"color:#b45309\"><b>Citat dana</b>: pomoćni servis nije pokrenut na portu "
                    + AUX_PORT
                    + ".</p>";
        }
    }

    private static class Quote {
        private final String text;
        private final String author;

        private Quote(String text, String author) {
            this.text = text;
            this.author = author;
        }
    }
}

