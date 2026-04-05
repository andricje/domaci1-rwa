package quotes;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.util.Random;
import org.json.JSONObject;

/**
 * Pomoćni servis: jedan endpoint vraća nasumično odabran citat dana kao JSON.
 * Sluša na localhost:8081; klijent (browser) ne komunicira direktno — samo glavni servis.
 */
public final class AuxiliaryServer {

    private static final int PORT = 8081;
    private static final String PATH = "/qod";

    private static final String[][] POOL = {
        {"Budi promena koju želiš da vidiš u svetu.", "Mahatma Gandhi"},
        {"Jedini način da uradite sjajan posao je da volite ono što radite.", "Steve Jobs"},
        {"Ne čekaj; pravo vreme nikada neće doći.", "Napoleon Hill"},
        {"Jednostavnost je vrhunac sofisticiranosti.", "Leonardo da Vinci"},
        {"Znanje je moć.", "Francis Bacon"}
    };

    public static void main(String[] args) throws IOException {
        try (ServerSocket server = new ServerSocket(PORT)) {
            logAux("slušam na portu " + PORT + " — http://localhost:" + PORT + PATH);
            Random rnd = new Random();
            while (true) {
                try (Socket client = server.accept()) {
                    handle(client, rnd);
                }
            }
        }
    }

    private static void logAux(String msg) {
        System.out.println(LocalTime.now() + " [pomoćni] " + msg);
    }

    private static void handle(Socket client, Random rnd) throws IOException {
        String peer = String.valueOf(client.getRemoteSocketAddress());
        BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream(), StandardCharsets.ISO_8859_1));
        String line = in.readLine();
        if (line == null) {
            logAux(peer + " → (prazan zahtev)");
            return;
        }
        logAux(peer + " → " + line);
        String[] parts = line.split(" ");
        if (parts.length < 2 || !"GET".equalsIgnoreCase(parts[0])) {
            logAux("odgovor 405 Method Not Allowed");
            send(client.getOutputStream(), 405, "Method Not Allowed", "text/plain", "Method Not Allowed");
            return;
        }
        String path = parts[1];
        if (path.contains("?")) {
            path = path.substring(0, path.indexOf('?'));
        }
        if (!PATH.equals(path)) {
            logAux("odgovor 404 za putanju " + path);
            send(client.getOutputStream(), 404, "Not Found", "text/plain", "Not Found");
            return;
        }
        while (true) {
            String h = in.readLine();
            if (h == null || h.isEmpty()) {
                break;
            }
        }
        int i = rnd.nextInt(POOL.length);
        JSONObject json = new JSONObject();
        json.put("quote", POOL[i][0]);
        json.put("author", POOL[i][1]);
        String payload = json.toString();
        logAux("odgovor 200 OK (JSON " + payload.length() + " B) — izabrano #" + (i + 1) + "/" + POOL.length + ", autor: " + POOL[i][1]);
        send(client.getOutputStream(), 200, "OK", "application/json; charset=utf-8", payload);
    }

    private static void send(OutputStream out, int status, String reason, String contentType, String body)
            throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        PrintWriter w = new PrintWriter(new OutputStreamWriter(out, StandardCharsets.ISO_8859_1));
        w.print("HTTP/1.1 " + status + " " + reason + "\r\n");
        w.print("Content-Type: " + contentType + "\r\n");
        w.print("Content-Length: " + bytes.length + "\r\n");
        w.print("Connection: close\r\n");
        w.print("\r\n");
        w.flush();
        out.write(bytes);
        out.flush();
    }
}
