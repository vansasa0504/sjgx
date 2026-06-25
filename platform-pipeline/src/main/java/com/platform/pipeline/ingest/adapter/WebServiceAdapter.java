package com.platform.pipeline.ingest.adapter;

import com.platform.pipeline.ingest.ProtocolAdapter;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class WebServiceAdapter implements ProtocolAdapter {
    private final HttpClient client = HttpClient.newHttpClient();

    @Override
    public String protocol() { return "WEBSERVICE"; }

    @Override
    public String fetch(URI endpoint) throws IOException, InterruptedException {
        String body = "<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\"><soap:Body/></soap:Envelope>";
        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .header("Content-Type", "text/xml;charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("WebService status " + response.statusCode());
        }
        return response.body();
    }
}
