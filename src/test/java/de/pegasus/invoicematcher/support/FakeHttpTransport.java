package de.pegasus.invoicematcher.support;

import io.github.raindancer118.aigateway.http.HttpTransport;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Test-Double fuer {@link HttpTransport}: liefert vorbereitete Antworten aus und merkt sich
 * jede empfangene Anfrage, damit ai-gateway-basierte Klassen ohne echtes Netzwerk getestet
 * werden koennen - gleiches Muster wie ai-gateways eigene {@code FakeHttpTransport}.
 */
public final class FakeHttpTransport implements HttpTransport {

    private final Deque<CannedResponse> responses = new ArrayDeque<>();
    private final List<HttpRequest> receivedRequests = new ArrayList<>();

    public FakeHttpTransport queue(int status, String body) {
        return queue(status, body, Map.of());
    }

    public FakeHttpTransport queue(int status, String body, Map<String, String> headers) {
        responses.addLast(new CannedResponse(status, body, headers));
        return this;
    }

    public List<HttpRequest> receivedRequests() {
        return receivedRequests;
    }

    @Override
    public HttpResponse<String> send(HttpRequest request) throws IOException {
        receivedRequests.add(request);
        CannedResponse canned = responses.pollFirst();
        if (canned == null) {
            throw new IOException("FakeHttpTransport: keine weitere Antwort in der Warteschlange");
        }
        return canned.toResponse(request);
    }

    private record CannedResponse(int status, String body, Map<String, String> headers) {

        HttpResponse<String> toResponse(HttpRequest request) {
            return new FakeResponse(status, body, headers, request);
        }
    }

    private record FakeResponse(int statusCode, String body, Map<String, String> headerMap,
                                HttpRequest request) implements HttpResponse<String> {

        @Override
        public int statusCode() {
            return statusCode;
        }

        @Override
        public HttpRequest request() {
            return request;
        }

        @Override
        public Optional<HttpResponse<String>> previousResponse() {
            return Optional.empty();
        }

        @Override
        public HttpHeaders headers() {
            Map<String, List<String>> multi = headerMap.entrySet().stream()
                    .collect(Collectors.toMap(Map.Entry::getKey, e -> List.of(e.getValue())));
            return HttpHeaders.of(multi, (a, b) -> true);
        }

        @Override
        public String body() {
            return body;
        }

        @Override
        public Optional<javax.net.ssl.SSLSession> sslSession() {
            return Optional.empty();
        }

        @Override
        public java.net.URI uri() {
            return request.uri();
        }

        @Override
        public HttpClient.Version version() {
            return HttpClient.Version.HTTP_1_1;
        }
    }
}
