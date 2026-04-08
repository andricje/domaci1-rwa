package http;

import app.RequestHandler;
import http.response.Response;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.StringTokenizer;

public class ServerThread implements Runnable {

    private Socket client;
    private BufferedReader in;
    private PrintWriter out;

    public ServerThread(Socket sock) {
        this.client = sock;

        try {
            //inicijalizacija ulaznog toka
            in = new BufferedReader(
                    new InputStreamReader(
                            client.getInputStream()));

            //inicijalizacija izlaznog sistema
            out = new PrintWriter(
                    new BufferedWriter(
                            new OutputStreamWriter(
                                    client.getOutputStream())), true);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void run() {
        try {
            // uzimamo samo prvu liniju zahteva, iz koje dobijamo HTTP method i putanju
            String requestLine = in.readLine();
            if (requestLine == null || requestLine.isEmpty()) {
                return;
            }

            StringTokenizer stringTokenizer = new StringTokenizer(requestLine);

            String method = stringTokenizer.nextToken();
            String path = stringTokenizer.nextToken();

            System.out.println("\nHTTP ZAHTEV KLIJENTA:\n");
            System.out.println(requestLine);

            Map<String, String> headers = new LinkedHashMap<>();
            while (true) {
                String h = in.readLine();
                if (h == null || h.trim().isEmpty()) {
                    break;
                }
                System.out.println(h);
                int c = h.indexOf(':');
                if (c > 0) {
                    headers.put(h.substring(0, c).trim().toLowerCase(), h.substring(c + 1).trim());
                }
            }

            String body = "";
            if (method.equals(HttpMethod.POST.toString())) {
                int len = 0;
                try {
                    String cl = headers.get("content-length");
                    if (cl != null) {
                        len = Integer.parseInt(cl.trim());
                    }
                } catch (NumberFormatException ignored) {
                    len = 0;
                }
                if (len > 0) {
                    char[] buffer = new char[len];
                    int read = 0;
                    while (read < len) {
                        int n = in.read(buffer, read, len - read);
                        if (n < 0) {
                            break;
                        }
                        read += n;
                    }
                    body = new String(buffer, 0, read);
                    System.out.println();
                    System.out.println(body);
                }
            }

            // `BufferedReader` radi nad bajtovima ulaza; telo forme je tipično UTF-8 pa ga tretiramo kao tekst.
            // Header vrednosti se čuvaju u lowercase map-i radi lakšeg pristupa.
            Request request = new Request(HttpMethod.valueOf(method), path, headers, body);

            RequestHandler requestHandler = new RequestHandler();
            Response response = requestHandler.handle(request);

            System.out.println("\nHTTP odgovor:\n");
            System.out.println(response.getResponseString());

            out.println(response.getResponseString());

            in.close();
            out.close();
            client.close();

        } catch (IOException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
