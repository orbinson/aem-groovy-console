package be.orbinson.aem.groovy.console.it;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import org.apache.commons.codec.binary.Base64;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

class GroovyConsoleServiceIT {

    private static final int SLING_PORT = Integer.getInteger("HTTP_PORT", 8080);

    @BeforeEach
    void beforeEach() throws IOException {
        await().atMost(30, TimeUnit.SECONDS)  // Set maximum wait time to 30 seconds
                .pollInterval(5, TimeUnit.SECONDS)
                .untilAsserted(() -> assertTrue(servicesAreAvailable()));
    }

    @Test
    void testScriptReturnsResult() throws Exception {

        String script = "print 'test'";
        Map response = executeScript(script);
        System.out.println("Got response script  at " + Instant.now());

        assertNotNull(response, "Could not get response from API");
        assertEquals("test", response.get("output"));
    }

    private static boolean servicesAreAvailable() throws IOException {
        System.out.println("Checking if services are available");
        try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
            HttpGet printHealth = new HttpGet("http://localhost:" + SLING_PORT + "/system/health.json?tags=systemalive,bundles");
            printHealth.addHeader("Authorization", "Basic " + Base64.encodeBase64String("admin:admin".getBytes(StandardCharsets.UTF_8)));
            try (CloseableHttpResponse response = httpclient.execute(printHealth)) {
                String body = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                Map jsonResponse = new Gson().fromJson(body, Map.class);
                try {
                    boolean systemAlive = "OK".equals(jsonResponse.get("overallResult"));
                    if (!systemAlive) {
                        System.out.println("Not alive yet:");
                        System.out.println(body);
                    }
                    return systemAlive;
                } catch (JsonSyntaxException e) {
                    return false;
                }
            }
        }
    }


    private static Map executeScript(String script) throws IOException {
        try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
            HttpPost executeScript = new HttpPost("http://localhost:" + SLING_PORT + "/bin/groovyconsole/post");
            List<BasicNameValuePair> basicNameValuePairs = new java.util.ArrayList<>();
            basicNameValuePairs.add(new BasicNameValuePair("script", script));
            executeScript.setEntity(new UrlEncodedFormEntity(basicNameValuePairs, StandardCharsets.UTF_8));
            executeScript.addHeader("Authorization", "Basic " + Base64.encodeBase64String("admin:admin".getBytes(StandardCharsets.UTF_8)));

            try (CloseableHttpResponse response = httpclient.execute(executeScript)) {
                System.out.println(response.getStatusLine());

                String body = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                try {
                    return new Gson().fromJson(body, Map.class);
                } catch (JsonSyntaxException e) {
                    System.out.println("Could not parse body from JSON: " + e.getMessage());
                    return null;
                }
            }
        }
    }
}
