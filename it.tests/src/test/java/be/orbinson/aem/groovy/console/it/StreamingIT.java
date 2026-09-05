package be.orbinson.aem.groovy.console.it;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.commons.codec.binary.Base64;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for asynchronous (streaming) script execution.
 */
// Thread.sleep is intentional here: testAsyncExecutionStreamsOutput polls a remote endpoint and
// accumulates partial output observed over time as the script executes asynchronously, which needs
// stateful accumulation (received output, offset) rather than a simple boolean predicate.
@SuppressWarnings("java:S2925")
class StreamingIT {

    private static final int SLING_PORT = Integer.getInteger("HTTP_PORT", 8080);
    private static final String BASE_URL = "http://localhost:" + SLING_PORT;
    private static final String AUTH_HEADER = "Basic " + Base64.encodeBase64String("admin:admin".getBytes(StandardCharsets.UTF_8));

    private static CloseableHttpClient httpClient;

    private static final AtomicInteger READY_STREAK = new AtomicInteger();

    @BeforeAll
    static void setUp() {
        httpClient = HttpClients.createDefault();

        // Cold start briefly registers the console servlets, then a framework-level cascade (resource
        // resolver / repository settling) unregisters and re-registers them a few seconds later. A single
        // readiness hit can succeed during that transient window and then break, so require several
        // consecutive successes -- spanning past the cascade -- before running the (non-retrying) tests.
        await().atMost(180, TimeUnit.SECONDS)
                .pollInterval(2, TimeUnit.SECONDS)
                .until(() -> {
                    if (isGroovyConsoleReady()) {
                        return READY_STREAK.incrementAndGet() >= 3;
                    }
                    READY_STREAK.set(0);
                    return false;
                });
    }

    @AfterAll
    static void tearDown() throws IOException {
        if (httpClient != null) {
            httpClient.close();
        }
    }

    @Test
    void testAsyncExecutionStreamsOutput() throws Exception {
        // a script that emits output progressively
        JsonObject start = postScript(
                "5.times {\n println \"tick $it\"\n Thread.sleep(400)\n}\nreturn \"finished\"", true);

        assertNotNull(start);
        assertTrue(start.has("executionId"), "Expected executionId for async execution");
        String executionId = start.get("executionId").getAsString();

        // poll until done, capturing whether partial output was observed before completion
        StringBuilder received = new StringBuilder();
        boolean sawPartial = false;
        int offset = 0;
        JsonObject finalPoll = null;

        long deadline = System.currentTimeMillis() + 60_000;
        while (System.currentTimeMillis() < deadline) {
            JsonObject poll = pollStream(executionId, offset);

            // the streaming registry can briefly 404 a just-started execution while the OSGi component
            // settles right after cold startup; keep polling until the deadline rather than failing
            if (poll == null) {
                Thread.sleep(250);
                continue;
            }

            received.append(poll.get("chunk").getAsString());
            offset = poll.get("offset").getAsInt();

            if (poll.get("done").getAsBoolean()) {
                finalPoll = poll;
                break;
            }
            if (received.length() > 0) {
                sawPartial = true;
            }
            Thread.sleep(250);
        }

        assertNotNull(finalPoll, "Execution did not finish in time");
        assertTrue(sawPartial, "Expected partial output before the execution finished");

        for (int i = 0; i < 5; i++) {
            assertTrue(received.toString().contains("tick " + i), "Missing output: tick " + i);
        }

        JsonObject response = finalPoll.getAsJsonObject("response");
        assertNotNull(response, "Expected full run response once done");
        assertEquals("finished", response.get("result").getAsString());
        assertTrue(response.get("output").getAsString().contains("tick 4"));
    }

    @Test
    void testSyncExecutionUnchangedForExternalClients() throws Exception {
        // backwards compatibility: without async=true the response is the full result, no executionId
        JsonObject response = postScript("println 'sync'\nreturn 7", false);

        assertNotNull(response);
        assertFalse(response.has("executionId"), "Sync execution must not return an executionId");
        assertEquals("7", response.get("result").getAsString());
        assertEquals("sync\n", response.get("output").getAsString());
    }

    @Test
    void testStreamEndpointRequiresPermission() throws Exception {
        HttpGet get = new HttpGet(BASE_URL + "/bin/groovyconsole/stream?executionId=unknown");
        // no Authorization header

        try (CloseableHttpResponse response = httpClient.execute(get)) {
            int status = response.getStatusLine().getStatusCode();
            assertTrue(status == 401 || status == 403,
                    "Expected 401/403 for unauthenticated stream request but got " + status);
            EntityUtils.consume(response.getEntity());
        }
    }

    @Test
    void testStreamEndpointReturnsNotFoundForUnknownId() throws Exception {
        HttpGet get = new HttpGet(BASE_URL + "/bin/groovyconsole/stream?executionId=does-not-exist");
        get.addHeader("Authorization", AUTH_HEADER);

        try (CloseableHttpResponse response = httpClient.execute(get)) {
            assertEquals(404, response.getStatusLine().getStatusCode());
            EntityUtils.consume(response.getEntity());
        }
    }

    private static boolean isGroovyConsoleReady() {
        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpPost post = new HttpPost(BASE_URL + "/bin/groovyconsole/post.json");
            List<BasicNameValuePair> params = new ArrayList<>();
            params.add(new BasicNameValuePair("script", "return 'ready'"));
            post.setEntity(new UrlEncodedFormEntity(params, StandardCharsets.UTF_8));
            post.addHeader("Authorization", AUTH_HEADER);
            post.addHeader("Connection", "close");
            try (CloseableHttpResponse response = client.execute(post)) {
                if (response.getStatusLine().getStatusCode() != 200) {
                    return false;
                }
                String body = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                JsonObject json = JsonParser.parseString(body).getAsJsonObject();
                return "ready".equals(json.get("result").getAsString());
            }
        } catch (Exception e) {
            return false;
        }
    }

    private static JsonObject postScript(String script, boolean async) throws IOException {
        HttpPost post = new HttpPost(BASE_URL + "/bin/groovyconsole/post.json");
        List<BasicNameValuePair> params = new ArrayList<>();
        params.add(new BasicNameValuePair("script", script));
        if (async) {
            params.add(new BasicNameValuePair("async", "true"));
        }
        post.setEntity(new UrlEncodedFormEntity(params, StandardCharsets.UTF_8));
        post.addHeader("Authorization", AUTH_HEADER);

        try (CloseableHttpResponse response = httpClient.execute(post)) {
            assertEquals(200, response.getStatusLine().getStatusCode());
            String body = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
            return JsonParser.parseString(body).getAsJsonObject();
        }
    }

    private static JsonObject pollStream(String executionId, int offset) throws IOException {
        HttpGet get = new HttpGet(BASE_URL + "/bin/groovyconsole/stream.json?executionId=" + executionId
                + "&offset=" + offset);
        get.addHeader("Authorization", AUTH_HEADER);

        try (CloseableHttpResponse response = httpClient.execute(get)) {
            int status = response.getStatusLine().getStatusCode();
            // a transient 404 means the execution is not (yet) pollable; let the caller retry
            if (status == 404) {
                EntityUtils.consumeQuietly(response.getEntity());
                return null;
            }
            assertEquals(200, status);
            String body = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
            return JsonParser.parseString(body).getAsJsonObject();
        }
    }
}
