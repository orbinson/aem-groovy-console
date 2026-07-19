package be.orbinson.aem.groovy.console.it;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.commons.codec.binary.Base64;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
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

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end index audit against the LIVE instance. Deploys a Groovy script, then runs an audit harness that (1) runs
 * that script while capturing the JCR queries it executes, and (2) EXPLAINs each against the instance's real Oak
 * indexes. Demonstrates the goal: verifying that the indexes a migration/report script relies on actually exist on
 * the target instance — without adding EXPLAIN to the script itself.
 */
class IndexDetectionIT {

    private static final int SLING_PORT = Integer.getInteger("HTTP_PORT", 8080);
    private static final String BASE_URL = "http://localhost:" + SLING_PORT;
    private static final String AUTH_HEADER =
            "Basic " + Base64.encodeBase64String("admin:admin".getBytes(StandardCharsets.UTF_8));

    private static final String TARGET_PATH = "/var/groovyconsole/it/AuditTarget.groovy";
    private static final String INDEX_PATH = "/oak:index/auditMarkerIt";

    private static CloseableHttpClient httpClient;

    @BeforeAll
    static void setUp() throws IOException {
        httpClient = HttpClients.createDefault();
        waitForReadiness(300);
        deployTargetScript();
    }

    @AfterAll
    static void tearDown() throws IOException {
        SlingContent.delete(httpClient, BASE_URL, AUTH_HEADER, TARGET_PATH);
        SlingContent.delete(httpClient, BASE_URL, AUTH_HEADER, INDEX_PATH);
        if (httpClient != null) {
            httpClient.close();
        }
    }

    @Test
    void detectsMissingIndexForTheScriptThenCoverageAfterCreatingIt() throws Exception {
        // 1. No custom index yet -> the script's query traverses -> the audit flags it as needing an index.
        JsonObject before = auditedQuery();
        System.out.println("[index-it] statement   = " + before.get("statement").getAsString());
        System.out.println("[index-it] plan before  = " + before.get("plan").getAsString());
        assertTrue(before.get("needsIndex").getAsBoolean(),
                "the query filters on a non-indexed property, so it should need an index; plan was: "
                        + before.get("plan").getAsString());

        // 2. Create the property index the script needs.
        createPropertyIndex();

        // 3. The same script's query is now covered on the live instance.
        JsonObject after = auditedQuery();
        System.out.println("[index-it] plan after   = " + after.get("plan").getAsString());
        assertFalse(after.get("needsIndex").getAsBoolean(),
                "with the property index installed the query should be covered; plan was: "
                        + after.get("plan").getAsString());
    }

    /** Audit the deployed target script via the query-audit servlet; assert one query was audited and return it. */
    private JsonObject auditedQuery() throws IOException {
        JsonObject response = post("/bin/groovyconsole/query-audit", param("scriptPath", TARGET_PATH));
        assertEquals("", response.get("exceptionStackTrace").getAsString(), "audited script failed");
        JsonArray queries = response.getAsJsonArray("queries");
        assertEquals(1, queries.size(), "expected exactly one audited query, got: " + queries);
        return queries.get(0).getAsJsonObject();
    }

    private static void deployTargetScript() throws IOException {
        SlingContent.deployFile(httpClient, BASE_URL, AUTH_HEADER, TARGET_PATH, "/scripts/audit-target.groovy");
    }

    private void createPropertyIndex() throws IOException {
        JsonObject response = executeScript(SlingContent.read("/scripts/create-audit-index.groovy"));
        assertEquals("", response.get("exceptionStackTrace").getAsString(), "index creation failed");
    }

    private static void waitForReadiness(long timeoutSec) {
        await().atMost(timeoutSec, TimeUnit.SECONDS)
                .pollInterval(1, TimeUnit.SECONDS)
                .ignoreExceptions()
                .untilAsserted(() -> assertEquals("ready", executeScript("return 'ready'").get("result").getAsString()));
    }

    private static JsonObject executeScript(String script) throws IOException {
        return post("/bin/groovyconsole/post", param("script", script));
    }

    private static BasicNameValuePair param(String name, String value) {
        return new BasicNameValuePair(name, value);
    }

    private static JsonObject post(String path, BasicNameValuePair... params) throws IOException {
        HttpPost httpPost = new HttpPost(BASE_URL + path);
        List<BasicNameValuePair> list = new ArrayList<>();
        for (BasicNameValuePair p : params) {
            list.add(p);
        }
        httpPost.setEntity(new UrlEncodedFormEntity(list, StandardCharsets.UTF_8));
        httpPost.addHeader("Authorization", AUTH_HEADER);
        httpPost.addHeader("Connection", "close");
        try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
            if (response.getStatusLine().getStatusCode() != 200) {
                return null;
            }
            String body = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
            return JsonParser.parseString(body).getAsJsonObject();
        }
    }
}
