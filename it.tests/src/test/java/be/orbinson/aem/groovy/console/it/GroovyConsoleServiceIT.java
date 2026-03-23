package be.orbinson.aem.groovy.console.it;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

class GroovyConsoleServiceIT {

    private static final int SLING_PORT = Integer.getInteger("HTTP_PORT", 8080);
    private static final String BASE_URL = "http://localhost:" + SLING_PORT;
    private static final String AUTH_HEADER = "Basic " + Base64.encodeBase64String("admin:admin".getBytes(StandardCharsets.UTF_8));

    // Users and groups created via repoinit in groovyconsole-it.json feature model
    private static final String UNPRIVILEGED_USER = "it-test-unprivileged";
    private static final String CLOUD_USER = "it-test-cloud-user";
    private static final String TEST_PASSWORD = "ItTest1234";

    private static CloseableHttpClient httpClient;

    @BeforeAll
    static void setUp() {
        httpClient = HttpClients.createDefault();

        // Wait for the Sling Starter and the Groovy Console content package to be fully installed
        await().atMost(120, TimeUnit.SECONDS)
                .pollInterval(5, TimeUnit.SECONDS)
                .untilAsserted(() -> assertTrue(isHealthy(), "System not healthy"));
    }

    @AfterAll
    static void tearDown() throws IOException {
        if (httpClient != null) {
            httpClient.close();
        }
    }

    @Test
    void testScriptReturnsOutput() throws Exception {
        JsonObject response = executeScript("print 'hello world'");

        assertNotNull(response, "Could not get response from API");
        assertEquals("hello world", response.get("output").getAsString());
    }

    @Test
    void testScriptReturnsResult() throws Exception {
        JsonObject response = executeScript("return 42");

        assertNotNull(response, "Could not get response from API");
        assertEquals("42", response.get("result").getAsString());
    }

    @Test
    void testScriptCompilationError() throws Exception {
        JsonObject response = executeScript("def {invalid");

        assertNotNull(response, "Could not get response from API");
        String exceptionStackTrace = response.get("exceptionStackTrace").getAsString();
        assertFalse(exceptionStackTrace.isEmpty(), "Expected a compilation error");
    }

    @Test
    void testScriptRuntimeError() throws Exception {
        JsonObject response = executeScript("1 / 0");

        assertNotNull(response, "Could not get response from API");
        String exceptionStackTrace = response.get("exceptionStackTrace").getAsString();
        assertTrue(exceptionStackTrace.contains("ArithmeticException"), "Expected ArithmeticException in stack trace");
    }

    @Test
    void testSessionBindingAvailable() throws Exception {
        JsonObject response = executeScript("return session != null");

        assertNotNull(response, "Could not get response from API");
        assertEquals("true", response.get("result").getAsString());
    }

    @Test
    void testResourceResolverBindingAvailable() throws Exception {
        JsonObject response = executeScript("return resourceResolver != null");

        assertNotNull(response, "Could not get response from API");
        assertEquals("true", response.get("result").getAsString());
    }

    @Test
    void testBundleContextBindingAvailable() throws Exception {
        JsonObject response = executeScript("return bundleContext != null");

        assertNotNull(response, "Could not get response from API");
        assertEquals("true", response.get("result").getAsString());
    }

    @Test
    void testJcrNodeCreation() throws Exception {
        // Create a node
        JsonObject createResponse = executeScript(
                "session.getNode('/').addNode('test-it-node', 'nt:unstructured')\n" +
                "session.save()\n" +
                "return session.nodeExists('/test-it-node')"
        );

        assertNotNull(createResponse, "Could not get response from API");
        assertEquals("", createResponse.get("exceptionStackTrace").getAsString());
        assertEquals("true", createResponse.get("result").getAsString());

        // Clean up
        JsonObject cleanupResponse = executeScript(
                "session.getNode('/test-it-node').remove()\n" +
                "session.save()\n" +
                "return !session.nodeExists('/test-it-node')"
        );

        assertNotNull(cleanupResponse, "Could not get response from API");
        assertEquals("true", cleanupResponse.get("result").getAsString());
    }

    @Test
    void testAuditRecordCreated() throws Exception {
        // Execute a script to generate an audit record
        executeScript("return 'audit-test'");

        // Fetch audit records
        JsonObject auditResponse = doGet("/bin/groovyconsole/audit");

        assertNotNull(auditResponse, "Could not get audit response");
        assertTrue(auditResponse.has("data"), "Expected audit response to contain 'data'");
        assertFalse(auditResponse.getAsJsonArray("data").isEmpty(), "Expected at least one audit record");
    }

    @Test
    void testTableOutput() throws Exception {
        JsonObject response = executeScript(
                "table {\n" +
                "    columns 'Name', 'Value'\n" +
                "    row 'foo', '1'\n" +
                "    row 'bar', '2'\n" +
                "}"
        );

        assertNotNull(response, "Could not get response from API");
        assertEquals("", response.get("exceptionStackTrace").getAsString());

        String result = response.get("result").getAsString();
        JsonObject resultJson = JsonParser.parseString(result).getAsJsonObject();
        JsonObject table = resultJson.getAsJsonObject("table");

        assertEquals(2, table.getAsJsonArray("columns").size());
        assertEquals(2, table.getAsJsonArray("rows").size());
    }

    @Test
    void testGroovyJsonStarImport() throws Exception {
        JsonObject response = executeScript("return new groovy.json.JsonBuilder([a: 1]).toString()");

        assertNotNull(response, "Could not get response from API");
        assertEquals("", response.get("exceptionStackTrace").getAsString());
        assertEquals("{\"a\":1}", response.get("result").getAsString());
    }

    @Test
    void testUnauthenticatedUserCannotExecuteScript() throws Exception {
        HttpPost post = new HttpPost(BASE_URL + "/bin/groovyconsole/post");
        List<BasicNameValuePair> params = new ArrayList<>();
        params.add(new BasicNameValuePair("script", "return 1"));
        post.setEntity(new UrlEncodedFormEntity(params, StandardCharsets.UTF_8));
        // No Authorization header

        try (CloseableHttpResponse response = httpClient.execute(post)) {
            int status = response.getStatusLine().getStatusCode();
            assertTrue(status == 401 || status == 403,
                    "Expected 401 or 403 for unauthenticated request, got " + status);
        }
    }

    /**
     * Verifies that a user without any allowed group membership cannot execute scripts.
     * The 'it-test-unprivileged' user is created at startup via repoinit in groovyconsole.json.
     */
    @Test
    void testNonPrivilegedUserCannotExecuteScript() throws Exception {
        int status = executeScriptStatus("return 1", UNPRIVILEGED_USER, TEST_PASSWORD);
        assertTrue(status == 401 || status == 403,
                "Expected 401 or 403 for non-privileged user, got " + status);
    }

    /**
     * Verifies that a user in a group referenced by the 'aemCloudAdministrators' system property
     * (simulating the AEM Cloud environment variable) is automatically granted access.
     *
     * The Sling JVM is started with environmentVariable aemCloudAdministrators=it-test-cloud-group (see pom.xml),
     * and 'it-test-cloud-user' is added to 'it-test-cloud-group' via repoinit in groovyconsole-it.json.
     */
    @Test
    void testAemCloudProductAdministratorsGroupGrantsAccess() throws Exception {
        JsonObject response = executeScript("return 'cloud-access-granted'", CLOUD_USER, TEST_PASSWORD);
        assertNotNull(response, "Could not get response from API");
        assertEquals("", response.get("exceptionStackTrace").getAsString());
        assertEquals("cloud-access-granted", response.get("result").getAsString());
    }

    private static boolean isHealthy() {
        try {
            HttpGet healthCheck = new HttpGet(BASE_URL + "/system/health.json?tags=systemalive,groovyconsole");
            healthCheck.addHeader("Authorization", AUTH_HEADER);
            try (CloseableHttpResponse response = httpClient.execute(healthCheck)) {
                String body = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                JsonObject jsonResponse = JsonParser.parseString(body).getAsJsonObject();
                if (!"OK".equals(jsonResponse.get("overallResult").getAsString())) {
                    return false;
                }
            }
            // Also verify the ScriptPostServlet is actually registered and available.
            // The health check may pass before the servlet resource providers are bound
            // (returning 0 groovyconsole-tagged results counts as OK). A GET to the
            // endpoint returns 405 when the servlet is up, 404 when it is not yet ready.
            HttpGet servletCheck = new HttpGet(BASE_URL + "/bin/groovyconsole/post");
            servletCheck.addHeader("Authorization", AUTH_HEADER);
            try (CloseableHttpResponse response = httpClient.execute(servletCheck)) {
                int status = response.getStatusLine().getStatusCode();
                EntityUtils.consume(response.getEntity());
                return status != 404;
            }
        } catch (Exception e) {
            return false;
        }
    }

    private static JsonObject doGet(String path) throws IOException {
        HttpGet get = new HttpGet(BASE_URL + path);
        get.addHeader("Authorization", AUTH_HEADER);

        try (CloseableHttpResponse response = httpClient.execute(get)) {
            assertEquals(200, response.getStatusLine().getStatusCode(),
                    "Expected HTTP 200 but got " + response.getStatusLine());

            String body = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
            try {
                return JsonParser.parseString(body).getAsJsonObject();
            } catch (JsonSyntaxException e) {
                fail("Could not parse response body as JSON: " + body);
                return null;
            }
        }
    }

    private static int executeScriptStatus(String script, String user, String password) throws IOException {
        HttpPost post = new HttpPost(BASE_URL + "/bin/groovyconsole/post");
        post.addHeader("Authorization", authHeader(user, password));
        List<BasicNameValuePair> params = new ArrayList<>();
        params.add(new BasicNameValuePair("script", script));
        post.setEntity(new UrlEncodedFormEntity(params, StandardCharsets.UTF_8));
        try (CloseableHttpResponse response = httpClient.execute(post)) {
            EntityUtils.consume(response.getEntity());
            return response.getStatusLine().getStatusCode();
        }
    }

    private static JsonObject executeScript(String script) throws IOException {
        return executeScript(script, AUTH_HEADER);
    }

    private static JsonObject executeScript(String script, String user, String password) throws IOException {
        return executeScript(script, authHeader(user, password));
    }

    private static JsonObject executeScript(String script, String authHeader) throws IOException {
        HttpPost post = new HttpPost(BASE_URL + "/bin/groovyconsole/post");
        post.addHeader("Authorization", authHeader);
        List<BasicNameValuePair> params = new ArrayList<>();
        params.add(new BasicNameValuePair("script", script));
        post.setEntity(new UrlEncodedFormEntity(params, StandardCharsets.UTF_8));
        try (CloseableHttpResponse response = httpClient.execute(post)) {
            assertEquals(200, response.getStatusLine().getStatusCode(),
                    "Expected HTTP 200 but got " + response.getStatusLine());
            String body = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
            try {
                return JsonParser.parseString(body).getAsJsonObject();
            } catch (JsonSyntaxException e) {
                fail("Could not parse response body as JSON: " + body);
                return null;
            }
        }
    }

    private static String authHeader(String user, String password) {
        return "Basic " + Base64.encodeBase64String((user + ":" + password).getBytes(StandardCharsets.UTF_8));
    }
}
