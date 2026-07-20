package be.orbinson.aem.groovy.console.it;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.commons.codec.binary.Base64;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.StreamSupport;

import org.apache.http.message.BasicNameValuePair;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the code-assistance endpoints used by the modern UI.
 */
class AssistIT {

    private static final int SLING_PORT = Integer.getInteger("HTTP_PORT", 8080);
    private static final String BASE_URL = "http://localhost:" + SLING_PORT;
    private static final String AUTH_HEADER = "Basic " + Base64.encodeBase64String("admin:admin".getBytes(StandardCharsets.UTF_8));

    private static CloseableHttpClient httpClient;

    @BeforeAll
    static void setUp() {
        httpClient = HttpClients.createDefault();

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
    void testClassDictionaryContainsJcrNode() throws Exception {
        JsonObject response = doGet("/bin/groovyconsole/assist/classes?prefix=javax.jcr&limit=2000");

        assertNotNull(response);
        JsonArray classes = response.getAsJsonArray("classes");
        assertFalse(classes.isEmpty(), "Expected classes matching prefix javax.jcr");

        boolean hasNode = StreamSupport.stream(classes.spliterator(), false)
                .anyMatch(element -> "javax.jcr.Node".equals(element.getAsJsonObject().get("fqcn").getAsString()));

        assertTrue(hasNode, "Expected javax.jcr.Node in class dictionary");
    }

    @Test
    void testClassDictionarySimpleNamePrefix() throws Exception {
        JsonObject response = doGet("/bin/groovyconsole/assist/classes?prefix=ResourceResol");

        assertNotNull(response);
        JsonArray classes = response.getAsJsonArray("classes");

        boolean hasResourceResolver = StreamSupport.stream(classes.spliterator(), false)
                .anyMatch(element -> "org.apache.sling.api.resource.ResourceResolver"
                        .equals(element.getAsJsonObject().get("fqcn").getAsString()));

        assertTrue(hasResourceResolver, "Expected ResourceResolver match on simple name prefix");
    }

    @Test
    void testClassMembersIncludeJavaAndGroovyMethods() throws Exception {
        JsonObject response = doGet("/bin/groovyconsole/assist/members?class=javax.jcr.Node");

        assertNotNull(response);
        assertEquals("javax.jcr.Node", response.get("fqcn").getAsString());

        JsonArray members = response.getAsJsonArray("members");

        boolean hasGetNode = false;
        boolean hasGroovyMethod = false;

        for (int i = 0; i < members.size(); i++) {
            JsonObject member = members.get(i).getAsJsonObject();

            if ("getNode".equals(member.get("name").getAsString())) {
                hasGetNode = true;
            }
            if ("groovy".equals(member.get("source").getAsString())) {
                hasGroovyMethod = true;
            }
        }

        assertTrue(hasGetNode, "Expected getNode method on javax.jcr.Node");
        assertTrue(hasGroovyMethod, "Expected Groovy metaclass methods on javax.jcr.Node");
    }

    @Test
    void testContextContainsBindingsAndStarImports() throws Exception {
        JsonObject response = doGet("/bin/groovyconsole/assist/context");

        assertNotNull(response);

        JsonArray bindings = response.getAsJsonArray("bindings");
        boolean hasResourceResolver = StreamSupport.stream(bindings.spliterator(), false)
                .anyMatch(element -> "resourceResolver".equals(element.getAsJsonObject().get("name").getAsString()));
        assertTrue(hasResourceResolver, "Expected resourceResolver binding");

        JsonArray starImports = response.getAsJsonArray("starImports");
        boolean hasJcrImport = StreamSupport.stream(starImports.spliterator(), false)
                .anyMatch(element -> "javax.jcr".equals(element.getAsJsonObject().get("packageName").getAsString()));
        assertTrue(hasJcrImport, "Expected javax.jcr star import");
    }

    @Test
    void testCompileInvalidScriptReturnsMarkers() throws Exception {
        JsonObject response = compile("def {invalid");

        assertNotNull(response);
        assertFalse(response.get("ok").getAsBoolean(), "Expected compilation failure");

        JsonArray markers = response.getAsJsonArray("markers");
        assertFalse(markers.isEmpty(), "Expected at least one marker");

        JsonObject marker = markers.get(0).getAsJsonObject();
        assertEquals("error", marker.get("severity").getAsString());
        assertEquals(1, marker.get("startLineNumber").getAsInt());
    }

    @Test
    void testCompileValidScriptReturnsOk() throws Exception {
        JsonObject response = compile("return resourceResolver != null");

        assertNotNull(response);
        assertTrue(response.get("ok").getAsBoolean(), "Expected successful compilation");
        assertTrue(response.getAsJsonArray("markers").isEmpty(), "Expected no markers");
    }

    @Test
    void testCompileDoesNotExecuteScript() throws Exception {
        // compiling a script with side effects must not run it
        JsonObject response = compile(
                "session.getNode('/').addNode('assist-compile-should-not-exist', 'nt:unstructured')\n" +
                "session.save()");

        assertNotNull(response);
        assertTrue(response.get("ok").getAsBoolean(), "Script should compile");

        HttpGet get = new HttpGet(BASE_URL + "/assist-compile-should-not-exist.json");
        get.addHeader("Authorization", AUTH_HEADER);

        try (CloseableHttpResponse checkResponse = httpClient.execute(get)) {
            assertEquals(404, checkResponse.getStatusLine().getStatusCode(),
                    "Compile must not execute the script");
        }
    }

    @Test
    void testAssistEndpointsRequirePermission() throws Exception {
        for (String path : new String[]{
                "/bin/groovyconsole/assist/classes",
                "/bin/groovyconsole/assist/members?class=javax.jcr.Node",
                "/bin/groovyconsole/assist/context",
                "/bin/groovyconsole/services"
        }) {
            HttpGet get = new HttpGet(BASE_URL + path);
            // no Authorization header

            try (CloseableHttpResponse response = httpClient.execute(get)) {
                int status = response.getStatusLine().getStatusCode();
                assertTrue(status == 401 || status == 403,
                        "Expected 401/403 for unauthenticated request to " + path + " but got " + status);
                EntityUtils.consume(response.getEntity());
            }
        }
    }

    @Test
    void testDefaultUiServesClassicConsole() throws Exception {
        // the 19.x line ships only the classic Ace console; the default path must serve it, not the modern SPA
        String html = getHtml("/apps/groovyconsole.html");

        assertTrue(html.contains("script-editor"), "Expected the classic console editor on the default path");
        assertFalse(html.contains("<gc-app>"), "Did not expect the modern SPA shell on the 19.x classic line");
    }

    @Test
    void testClassicSelectorServesClassicConsole() throws Exception {
        String html = getHtml("/apps/groovyconsole.classic.html");

        assertTrue(html.contains("script-editor"), "Expected classic UI editor element");
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

    private static String getHtml(String path) throws IOException {
        HttpGet get = new HttpGet(BASE_URL + path);
        get.addHeader("Authorization", AUTH_HEADER);

        try (CloseableHttpResponse response = httpClient.execute(get)) {
            assertEquals(200, response.getStatusLine().getStatusCode(),
                    "Expected HTTP 200 for " + path + " but got " + response.getStatusLine());

            return EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
        }
    }

    private static JsonObject doGet(String path) throws IOException {
        HttpGet get = new HttpGet(BASE_URL + path);
        get.addHeader("Authorization", AUTH_HEADER);

        try (CloseableHttpResponse response = httpClient.execute(get)) {
            assertEquals(200, response.getStatusLine().getStatusCode(),
                    "Expected HTTP 200 for " + path + " but got " + response.getStatusLine());

            String body = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
            return JsonParser.parseString(body).getAsJsonObject();
        }
    }

    private static JsonObject compile(String script) throws IOException {
        HttpPost post = new HttpPost(BASE_URL + "/bin/groovyconsole/assist/compile");
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
