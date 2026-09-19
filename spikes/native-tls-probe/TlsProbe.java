import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * Minimal GraalVM Native Image TLS & Outbound HTTPS Probe.
 * Tests strict certificate verification against live endpoints (e.g. AMFI India)
 * without bypassing verification (-k).
 */
public class TlsProbe {
    public static void main(String[] args) {
        String target = args.length > 0 ? args[0] : "https://www.amfiindia.com/";
        System.out.println("Starting strict TLS handshake probe to: " + target);
        try {
            HttpClient client = HttpClient.newBuilder()
                    .version(HttpClient.Version.HTTP_1_1)
                    .build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(target))
                    .GET()
                    .build();
            HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
            System.out.println("TLS Handshake & HTTP Request SUCCESS! HTTP Status: " + response.statusCode());
            System.exit(0);
        } catch (Exception e) {
            System.err.println("TLS Handshake / HTTPS Request FAILED: " + e.getClass().getName() + " - " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
