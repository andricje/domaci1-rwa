package http;

import http.HttpMethod;

import java.util.Collections;
import java.util.Map;

public class Request {

    private final HttpMethod httpMethod;

    private final String path;

    private final Map<String, String> headers;
    private final String body;

    public Request(HttpMethod httpMethod, String path) {
        this(httpMethod, path, Collections.emptyMap(), "");
    }

    public Request(HttpMethod httpMethod, String path, Map<String, String> headers, String body) {
        this.httpMethod = httpMethod;
        this.path = path;
        this.headers = headers == null ? Collections.emptyMap() : headers;
        this.body = body == null ? "" : body;
    }

    public HttpMethod getHttpMethod() {
        return httpMethod;
    }

    public String getPath() {
        return path;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public String getBody() {
        return body;
    }
}
