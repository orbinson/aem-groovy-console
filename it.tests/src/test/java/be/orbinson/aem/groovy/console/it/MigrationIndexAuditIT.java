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
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the optional query-audit integration in the migration extension: a migration run started with
 * {@code measureIndexUsage=true} reports, per script, whether its queries are backed by an Oak index — the CI
 * validation the migration extension enables when the (optional) query-audit extension is installed.
 */
class MigrationIndexAuditIT {

    private static final int SLING_PORT = Integer.getInteger("HTTP_PORT", 8080);
    private static final String BASE_URL = "http://localhost:" + SLING_PORT;
    private static final String AUTH_HEADER =
            "Basic " + Base64.encodeBase64String("admin:admin".getBytes(StandardCharsets.UTF_8));

    // A migration script under the migration extension's default scripts base path. The ".always." token makes it
    // re-run on every migration run, so the run this test triggers always executes and audits it even if a
    // concurrent/background migration run (from another suite test still draining) executed it first.
    private static final String SCRIPT_NAME = "it-index-audit.always.groovy";
    private static final String SCRIPT_PATH = "/conf/groovyconsole/scripts/migration/" + SCRIPT_NAME;

    private static CloseableHttpClient httpClient;

    @BeforeAll
    static void setUp() throws IOException {
        httpClient = HttpClients.createDefault();
        waitForReadiness(300);
        deployMigrationScript();
    }

    @AfterAll
    static void tearDown() throws IOException {
        SlingContent.delete(httpClient, BASE_URL, AUTH_HEADER, SCRIPT_PATH);
        if (httpClient != null) {
            httpClient.close();
        }
    }

    @Test
    void migrationRunReportsIndexUsagePerScript() throws Exception {
        // A migration run is rejected with 409 while another run is in progress or queued (the service serialises
        // runs). Other migration tests in the suite may still be draining an async run, so retry until our run is
        // accepted rather than failing on that transient conflict.
        JsonObject run = await().atMost(120, TimeUnit.SECONDS)
                .pollInterval(2, TimeUnit.SECONDS)
                .ignoreExceptions()
                .until(() -> post("/bin/groovyconsole/migration", param("measureIndexUsage", "true")), notNullValue());
        assertNotNull(run, "no response from migration run");

        JsonArray results = run.getAsJsonArray("results");
        assertNotNull(results, "migration run had no results: " + run);

        JsonObject scriptResult = null;
        for (int i = 0; i < results.size(); i++) {
            JsonObject r = results.get(i).getAsJsonObject();
            if (r.get("scriptPath").getAsString().endsWith(SCRIPT_NAME)) {
                scriptResult = r;
                break;
            }
        }
        assertNotNull(scriptResult, "audited migration script not found in results: " + results);

        JsonArray queryAudit = scriptResult.getAsJsonArray("queryAudit");
        assertNotNull(queryAudit, "expected a queryAudit on the script result: " + scriptResult);
        assertEquals(1, queryAudit.size(), "expected one audited query, got: " + queryAudit);

        JsonObject query = queryAudit.get(0).getAsJsonObject();
        System.out.println("[migration-index-it] statement = " + query.get("statement").getAsString());
        System.out.println("[migration-index-it] plan      = " + query.get("plan").getAsString());
        assertTrue(query.get("needsIndex").getAsBoolean(),
                "the migration script's query filters on a non-indexed property, so it should be flagged; plan was: "
                        + query.get("plan").getAsString());
    }

    private static void deployMigrationScript() throws IOException {
        SlingContent.deployFile(httpClient, BASE_URL, AUTH_HEADER, SCRIPT_PATH, "/scripts/migration-index-audit.groovy");
    }

    private static JsonObject console(String script) throws IOException {
        return post("/bin/groovyconsole/post", param("script", script));
    }

    private static void waitForReadiness(long timeoutSec) {
        await().atMost(timeoutSec, TimeUnit.SECONDS)
                .pollInterval(1, TimeUnit.SECONDS)
                .ignoreExceptions()
                .untilAsserted(() -> assertEquals("ready", console("return 'ready'").get("result").getAsString()));
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
            int status = response.getStatusLine().getStatusCode();
            String body = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
            if (status != 200) {
                return null;
            }
            return JsonParser.parseString(body).getAsJsonObject();
        }
    }
}
