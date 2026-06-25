package com.platform.pipeline.ingest;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class HttpAdapter implements ProtocolAdapter {
    private final HttpClient client;

    public HttpAdapter() {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build());
    }

    HttpAdapter(HttpClient client) {
        this.client = client;
    }

    @Override
    public String protocol() {
        return "HTTP";
    }

    @Override
    public String fetch(URI endpoint) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(10)).GET().build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("HTTP status " + response.statusCode());
        }
        return response.body();
    }
}
