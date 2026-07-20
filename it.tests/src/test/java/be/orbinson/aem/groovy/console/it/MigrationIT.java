package be.orbinson.aem.groovy.console.it;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
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
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MigrationIT {

    private static final int SLING_PORT = Integer.getInteger("HTTP_PORT", 8080);
    private static final String BASE_URL = "http://localhost:" + SLING_PORT;
    private static final String AUTH_HEADER = "Basic " + Base64.encodeBase64String("admin:admin".getBytes(StandardCharsets.UTF_8));

    private static final String MIGRATION_ENDPOINT = "/bin/groovyconsole/migration";
    private static final String SCRIPTS_BASE_PATH = "/conf/groovyconsole/scripts/migration";
    private static final String APPS_SCRIPTS_BASE_PATH = "/apps/groovyconsole-migration-scripts";

    private static CloseableHttpClient httpClient;

    @BeforeAll
    static void setUp() {
        httpClient = HttpClients.createDefault();

        // All bundles/content are pre-converted into the launch feature (cp-converter), so there is no
        // post-startup content-package install cascade to wait out here, unlike the older jetty12-1.1.8-era
        // whiteboard-corruption workaround this used to carry -- a single successful check once the health
        // check reports OK and the endpoints are actually reachable is sufficient (see GroovyConsoleReportsIT).
        await().atMost(180, TimeUnit.SECONDS)
                .pollInterval(5, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    assertTrue(isHealthy(), "System not healthy");
                    assertTrue(endpointsAvailable(), "Groovy Console endpoints not available");
                });
    }

    @AfterAll
    static void tearDown() throws IOException {
        if (httpClient != null) {
            httpClient.close();
        }
    }

    @Test
    void testMigrationPageServedWithHashedEntry() throws Exception {
        String body = getHtml("/apps/groovyconsole/migrations.html");

        assertTrue(body.contains("<gcm-app>"), "Expected the migration app element");
        // the entry is content-hashed (migration-<hash>.js), resolved from the Vite manifest for cache-busting
        assertTrue(body.matches("(?s).*/apps/groovyconsole-migration/spa/assets/migration-[\\w-]+\\.js.*"),
                "Expected the hashed migration bundle script");
    }

    private static String getHtml(String path) throws IOException {
        HttpGet get = new HttpGet(BASE_URL + path);
        get.addHeader("Authorization", AUTH_HEADER);

        try (CloseableHttpResponse response = httpClient.execute(get)) {
            assertEquals(200, response.getStatusLine().getStatusCode());

            return EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
        }
    }

    @Test
    @Order(1)
    void testSyncRunExecutesNewScript() throws Exception {
        createMigrationScript("001-first.groovy", "println 'migration one'");

        JsonObject run = postMigration(200);

        assertEquals("SUCCESS", run.get("status").getAsString());
        assertTrue(run.get("executed").getAsInt() >= 1, "Expected at least one executed script");

        JsonObject result = findResult(run, "001-first.groovy");

        assertNotNull(result, "Expected a result for 001-first.groovy");
        assertEquals("SUCCESS", result.get("status").getAsString());
        assertTrue(result.get("output").getAsString().contains("migration one"));
        assertFalse(run.get("runId").getAsString().isEmpty());
    }

    @Test
    @Order(2)
    void testUnchangedScriptIsNotExecutedAgain() throws Exception {
        JsonObject run = postMigration(200);

        assertEquals("SUCCESS", run.get("status").getAsString());
        assertNull(findResult(run, "001-first.groovy"), "Unchanged script should not run again");
    }

    @Test
    @Order(3)
    void testChangedScriptIsExecutedAgain() throws Exception {
        createMigrationScript("001-first.groovy", "println 'migration one changed'");

        JsonObject run = postMigration(200);

        JsonObject result = findResult(run, "001-first.groovy");

        assertNotNull(result, "Changed script should run again");
        assertEquals("SUCCESS", result.get("status").getAsString());
    }

    @Test
    @Order(4)
    void testPendingAndDryRun() throws Exception {
        createMigrationScript("002-pending.groovy", "println 'migration two'");

        // pending listing contains the new script
        JsonObject pending = doGet(MIGRATION_ENDPOINT + "?pending=true");
        assertTrue(containsString(pending.getAsJsonArray("data"), SCRIPTS_BASE_PATH + "/002-pending.groovy"));

        // dry run reports it as PENDING without executing
        JsonObject dryRun = postMigration(200, new BasicNameValuePair("dryRun", "true"));
        JsonObject result = findResult(dryRun, "002-pending.groovy");

        assertNotNull(result);
        assertEquals("PENDING", result.get("status").getAsString());

        // still pending afterwards
        JsonObject pendingAfter = doGet(MIGRATION_ENDPOINT + "?pending=true");
        assertTrue(containsString(pendingAfter.getAsJsonArray("data"), SCRIPTS_BASE_PATH + "/002-pending.groovy"));
    }

    @Test
    @Order(5)
    void testRegistryListing() throws Exception {
        postMigration(200);

        JsonObject registry = doGet(MIGRATION_ENDPOINT + "?registry=true");
        JsonArray data = registry.getAsJsonArray("data");

        assertFalse(data.isEmpty(), "Expected registry entries");

        boolean found = false;
        for (JsonElement element : data) {
            JsonObject entry = element.getAsJsonObject();
            if (entry.get("scriptPath").getAsString().endsWith("001-first.groovy")) {
                found = true;
                assertEquals("SUCCESS", entry.get("status").getAsString());
                assertFalse(entry.get("pending").getAsBoolean());
            }
        }

        assertTrue(found, "Expected registry entry for 001-first.groovy");
    }

    @Test
    @Order(6)
    void testAsyncRunCanBePolled() throws Exception {
        createMigrationScript("003-async.groovy", "println 'migration three'");

        JsonObject queued = postMigration(202, new BasicNameValuePair("async", "true"));
        String runId = queued.get("runId").getAsString();

        assertFalse(runId.isEmpty());
        assertEquals("RUNNING", queued.get("status").getAsString());

        await().atMost(60, TimeUnit.SECONDS)
                .pollInterval(2, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    JsonObject run = doGet(MIGRATION_ENDPOINT + "?runId=" + runId);
                    assertNotEquals("RUNNING", run.get("status").getAsString(), "Run still in progress");
                });

        JsonObject run = doGet(MIGRATION_ENDPOINT + "?runId=" + runId);

        assertEquals("SUCCESS", run.get("status").getAsString());
        assertNotNull(findResult(run, "003-async.groovy"));
    }

    @Test
    @Order(7)
    void testFailFastSkipsRemainingScripts() throws Exception {
        createMigrationScript("010-failing.groovy", "throw new RuntimeException('intentional failure')");
        createMigrationScript("011-after-failure.groovy", "println 'should be skipped'");

        try {
            JsonObject run = postMigration(200);

            assertEquals("FAILED", run.get("status").getAsString());

            JsonObject failedResult = findResult(run, "010-failing.groovy");
            assertNotNull(failedResult);
            assertEquals("FAILED", failedResult.get("status").getAsString());
            assertTrue(failedResult.get("error").getAsString().contains("intentional failure"));

            JsonObject skippedResult = findResult(run, "011-after-failure.groovy");
            assertNotNull(skippedResult);
            assertEquals("SKIPPED", skippedResult.get("status").getAsString());

            // failed and skipped scripts are pending again
            JsonObject pending = doGet(MIGRATION_ENDPOINT + "?pending=true");
            assertTrue(containsString(pending.getAsJsonArray("data"), SCRIPTS_BASE_PATH + "/010-failing.groovy"));
            assertTrue(containsString(pending.getAsJsonArray("data"), SCRIPTS_BASE_PATH + "/011-after-failure.groovy"));
        } finally {
            // remove the failing scripts so subsequent runs succeed again
            deleteMigrationScript("010-failing.groovy");
            deleteMigrationScript("011-after-failure.groovy");
        }
    }

    @Test
    @Order(8)
    void testRunHistoryListing() throws Exception {
        JsonObject response = doGet(MIGRATION_ENDPOINT);

        assertFalse(response.get("running").getAsBoolean());
        assertFalse(response.getAsJsonArray("data").isEmpty(), "Expected at least one migration run in the history");
    }

    @Test
    @Order(9)
    void testHealthChecksReportOk() throws Exception {
        // ensure the last run is a successful one regardless of what earlier tests left behind (e.g. the
        // intentional failure in testFailFastSkipsRemainingScripts), so this assertion is deterministic
        postMigration(200);

        HttpGet healthCheck = new HttpGet(BASE_URL + "/system/health.json?tags=migration");
        healthCheck.addHeader("Authorization", AUTH_HEADER);

        try (CloseableHttpResponse response = httpClient.execute(healthCheck)) {
            assertEquals(200, response.getStatusLine().getStatusCode());

            String body = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
            JsonObject jsonResponse = JsonParser.parseString(body).getAsJsonObject();

            assertEquals("OK", jsonResponse.get("overallResult").getAsString());
            assertTrue(body.contains("AEM Groovy Console Migration - Last Run"),
                    "Expected the last-run health check to be present");
            assertTrue(body.contains("AEM Groovy Console Migration - Self Check"),
                    "Expected the self-check health check to be present");
        }
    }

    @Test
    @Order(10)
    void testImmutableAppsPathScriptsAreDiscovered() throws Exception {
        // scripts under the immutable /apps path are discovered alongside /conf by the default multi-path config
        createMigrationScriptAt(APPS_SCRIPTS_BASE_PATH, "100-apps-migration.groovy", "println 'migration from apps'");

        try {
            JsonObject run = postMigration(200);

            assertEquals("SUCCESS", run.get("status").getAsString());

            JsonObject result = findResult(run, "100-apps-migration.groovy");
            assertNotNull(result, "Expected the /apps script to be discovered and executed");
            assertEquals("SUCCESS", result.get("status").getAsString());
            assertEquals(APPS_SCRIPTS_BASE_PATH + "/100-apps-migration.groovy",
                    result.get("scriptPath").getAsString());
            assertTrue(result.get("output").getAsString().contains("migration from apps"));
        } finally {
            deleteMigrationScriptAt(APPS_SCRIPTS_BASE_PATH, "100-apps-migration.groovy");
        }
    }

    private static boolean endpointsAvailable() {
        try {
            // probe the console post servlet
            HttpPost post = new HttpPost(BASE_URL + "/bin/groovyconsole/post");
            List<BasicNameValuePair> params = new ArrayList<>();
            params.add(new BasicNameValuePair("script", "return true"));
            post.setEntity(new UrlEncodedFormEntity(params, StandardCharsets.UTF_8));
            post.addHeader("Authorization", AUTH_HEADER);

            try (CloseableHttpResponse response = httpClient.execute(post)) {
                EntityUtils.consume(response.getEntity());
                if (response.getStatusLine().getStatusCode() != 200) {
                    return false;
                }
            }

            // probe the migration servlet
            HttpGet get = new HttpGet(BASE_URL + MIGRATION_ENDPOINT);
            get.addHeader("Authorization", AUTH_HEADER);

            try (CloseableHttpResponse response = httpClient.execute(get)) {
                EntityUtils.consume(response.getEntity());
                return response.getStatusLine().getStatusCode() == 200;
            }
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean isHealthy() {
        try {
            HttpGet healthCheck = new HttpGet(BASE_URL + "/system/health.json?tags=systemalive,groovyconsole");
            healthCheck.addHeader("Authorization", AUTH_HEADER);
            try (CloseableHttpResponse response = httpClient.execute(healthCheck)) {
                String body = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                JsonObject jsonResponse = JsonParser.parseString(body).getAsJsonObject();
                return "OK".equals(jsonResponse.get("overallResult").getAsString());
            }
        } catch (Exception e) {
            return false;
        }
    }

    private static JsonObject findResult(JsonObject run, String scriptName) {
        for (JsonElement element : run.getAsJsonArray("results")) {
            JsonObject result = element.getAsJsonObject();
            if (result.get("scriptPath").getAsString().endsWith(scriptName)) {
                return result;
            }
        }
        return null;
    }

    private static boolean containsString(JsonArray array, String value) {
        for (JsonElement element : array) {
            if (value.equals(element.getAsString())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Create or replace a migration script file via the Groovy Console post servlet.  The script content is
     * base64-encoded to avoid any escaping issues.
     */
    private static void createMigrationScript(String name, String content) throws IOException {
        String encodedContent = Base64.encodeBase64String(content.getBytes(StandardCharsets.UTF_8));

        JsonObject response = executeScript(
                "def parent = session.getNode('" + SCRIPTS_BASE_PATH + "')\n" +
                "if (parent.hasNode('" + name + "')) { parent.getNode('" + name + "').remove() }\n" +
                "def file = parent.addNode('" + name + "', 'nt:file')\n" +
                "def resource = file.addNode('jcr:content', 'nt:resource')\n" +
                "def bytes = '" + encodedContent + "'.decodeBase64()\n" +
                "resource.setProperty('jcr:data', session.valueFactory.createBinary(new ByteArrayInputStream(bytes)))\n" +
                "session.save()"
        );

        assertEquals("", response.get("exceptionStackTrace").getAsString(),
                "Could not create migration script " + name);
    }

    private static void createMigrationScriptAt(String basePath, String name, String content) throws IOException {
        String encodedContent = Base64.encodeBase64String(content.getBytes(StandardCharsets.UTF_8));

        JsonObject response = executeScript(
                "import org.apache.jackrabbit.commons.JcrUtils\n" +
                "def parent = JcrUtils.getOrCreateByPath('" + basePath + "', 'sling:Folder', session)\n" +
                "if (parent.hasNode('" + name + "')) { parent.getNode('" + name + "').remove() }\n" +
                "def file = parent.addNode('" + name + "', 'nt:file')\n" +
                "def resource = file.addNode('jcr:content', 'nt:resource')\n" +
                "def bytes = '" + encodedContent + "'.decodeBase64()\n" +
                "resource.setProperty('jcr:data', session.valueFactory.createBinary(new ByteArrayInputStream(bytes)))\n" +
                "session.save()"
        );

        assertEquals("", response.get("exceptionStackTrace").getAsString(),
                "Could not create migration script " + name + " at " + basePath);
    }

    private static void deleteMigrationScriptAt(String basePath, String name) throws IOException {
        JsonObject response = executeScript(
                "def node = session.nodeExists('" + basePath + "/" + name + "') ? " +
                "session.getNode('" + basePath + "/" + name + "') : null\n" +
                "if (node) { node.remove(); session.save() }"
        );

        assertEquals("", response.get("exceptionStackTrace").getAsString(),
                "Could not delete migration script " + name + " at " + basePath);
    }

    private static void deleteMigrationScript(String name) throws IOException {
        JsonObject response = executeScript(
                "def parent = session.getNode('" + SCRIPTS_BASE_PATH + "')\n" +
                "if (parent.hasNode('" + name + "')) { parent.getNode('" + name + "').remove() }\n" +
                "session.save()"
        );

        assertEquals("", response.get("exceptionStackTrace").getAsString(),
                "Could not delete migration script " + name);
    }

    private static JsonObject postMigration(int expectedStatus, BasicNameValuePair... parameters) throws IOException {
        HttpPost post = new HttpPost(BASE_URL + MIGRATION_ENDPOINT);
        List<BasicNameValuePair> params = new ArrayList<>();
        for (BasicNameValuePair parameter : parameters) {
            params.add(parameter);
        }
        post.setEntity(new UrlEncodedFormEntity(params, StandardCharsets.UTF_8));
        post.addHeader("Authorization", AUTH_HEADER);

        try (CloseableHttpResponse response = httpClient.execute(post)) {
            assertEquals(expectedStatus, response.getStatusLine().getStatusCode(),
                    "Expected HTTP " + expectedStatus + " but got " + response.getStatusLine());

            String body = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
            return JsonParser.parseString(body).getAsJsonObject();
        }
    }

    private static JsonObject doGet(String path) throws IOException {
        HttpGet get = new HttpGet(BASE_URL + path);
        get.addHeader("Authorization", AUTH_HEADER);

        try (CloseableHttpResponse response = httpClient.execute(get)) {
            assertEquals(200, response.getStatusLine().getStatusCode(),
                    "Expected HTTP 200 but got " + response.getStatusLine());

            String body = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
            return JsonParser.parseString(body).getAsJsonObject();
        }
    }

    private static JsonObject executeScript(String script) throws IOException {
        HttpPost post = new HttpPost(BASE_URL + "/bin/groovyconsole/post");
        List<BasicNameValuePair> params = new ArrayList<>();
        params.add(new BasicNameValuePair("script", script));
        post.setEntity(new UrlEncodedFormEntity(params, StandardCharsets.UTF_8));
        post.addHeader("Authorization", AUTH_HEADER);

        try (CloseableHttpResponse response = httpClient.execute(post)) {
            assertEquals(200, response.getStatusLine().getStatusCode(),
                    "Expected HTTP 200 but got " + response.getStatusLine());

            String body = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
            return JsonParser.parseString(body).getAsJsonObject();
        }
    }
}
