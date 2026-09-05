package be.orbinson.aem.groovy.console.it;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.commons.codec.binary.Base64;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Validates the three-tier ACL setup provisioned for these integration tests (see
 * {@code groovyconsole-reports-acl-repoinit.txt} and {@code groovyconsole-reports-acl.json}), which implements
 * and extends the "Recommended ACL setup" documented in {@code extensions/reports/README.md}:
 *
 * <ul>
 *     <li><b>report-viewers</b> — {@code jcr:read} only: may run/view/export reports, nothing else.</li>
 *     <li><b>report-authors</b> — {@code jcr:modifyProperties} plus a deny ACE on {@code *.groovy*}: may edit
 *         report metadata (title, description, category, page size), but are denied ever changing the executable
 *         Groovy. They are <em>not</em> in the console's {@code allowedGroups}, so the reports servlet's
 *         script-editing endpoints reject them too — both layers described by the README are covered.</li>
 *     <li><b>report-admins</b> — {@code rep:write} and <em>are</em> in the console's {@code allowedGroups}: may
 *         create reports and edit the executable Groovy, both directly in the repository and through the reports
 *         servlet.</li>
 * </ul>
 *
 * <p>Each tier is checked against both boundaries the README calls out: the raw JCR ACLs (exercised here directly
 * through the OOTB {@code SlingPostServlet}, bypassing the reports servlet entirely) and the reports servlet's own
 * {@code /bin/groovyconsole/reports} endpoint (which additionally gates script writes on console permission).</p>
 */
class GroovyConsoleReportsAclIT {

    private static final int SLING_PORT = Integer.getInteger("HTTP_PORT", 8080);
    private static final String BASE_URL = "http://localhost:" + SLING_PORT;

    private static final String ADMIN_AUTH_HEADER =
            "Basic " + Base64.encodeBase64String("admin:admin".getBytes(StandardCharsets.UTF_8));

    private static final String REPORT_VIEWER_USER = "it-report-viewer";
    private static final String REPORT_AUTHOR_USER = "it-report-author";
    private static final String REPORT_ADMIN_USER = "it-report-admin";

    private static final String REPORT_NAME = "it-acl-report";
    private static final String REPORT_PATH = "/conf/groovyconsole/reports/" + REPORT_NAME;
    private static final String SCRIPT_NODE_PATH = REPORT_PATH + "/" + REPORT_NAME + ".groovy/jcr:content";

    private static final String ORIGINAL_SCRIPT =
            String.join("\n", "def data = report.data()", "data.column('N')", "data.row('1')", "data");

    private static CloseableHttpClient httpClient;

    @BeforeAll
    static void setUp() throws IOException {
        httpClient = HttpClients.createDefault();

        await().atMost(180, TimeUnit.SECONDS)
                .pollInterval(5, TimeUnit.SECONDS)
                .untilAsserted(() -> assertTrue(isReportsApiReady(), "Reports API not ready"));

        createReport();
    }

    @AfterAll
    static void tearDown() throws IOException {
        if (httpClient != null) {
            httpClient.close();
        }
    }

    // report-viewers: read-only, no write access at all

    @Test
    void testReportViewerCanReadReport() throws IOException {
        JsonObject definition = getJsonAs(REPORT_VIEWER_USER, "/bin/groovyconsole/reports.json?name=" + REPORT_NAME);

        assertEquals(REPORT_NAME, definition.get("name").getAsString());
        assertFalse(definition.get("canEdit").getAsBoolean(), "report-viewers must not be able to edit metadata");
        assertFalse(definition.get("canEditScript").getAsBoolean(), "report-viewers must not be able to edit scripts");
    }

    @Test
    void testReportViewerCannotEditMetadataDirectly() throws IOException {
        // report-viewers have no jcr:modifyProperties grant at all, so even metadata is out of reach
        int status = postFormAs(REPORT_VIEWER_USER, REPORT_PATH,
                new BasicNameValuePair("jcr:title", "Edited by report-viewer"));

        assertTrue(status >= 400, "report-viewers must be denied writing report properties but got " + status);
    }

    @Test
    void testReportViewerCannotEditGroovyScriptDirectly() throws IOException {
        int status = postFormAs(REPORT_VIEWER_USER, SCRIPT_NODE_PATH,
                new BasicNameValuePair("jcr:data", "throw new RuntimeException('should never run')"));

        assertTrue(status >= 400,
                "report-viewers must be denied writing the report's .groovy script content but got " + status);
    }

    @Test
    void testReportViewerCannotSaveViaReportsServlet() throws IOException {
        // the servlet path: no JCR write access at all means even the metadata-only fallback fails
        JsonObject body = new JsonObject();
        body.addProperty("name", REPORT_NAME);
        body.addProperty("title", "Edited via servlet by report-viewer");

        int status = postJsonAs(REPORT_VIEWER_USER, "/bin/groovyconsole/reports", body.toString());

        assertTrue(status >= 400,
                "report-viewers must be denied saving through the reports servlet but got " + status);
    }

    // report-authors: metadata edits allowed, executable Groovy denied — both directly and through the servlet

    @Test
    void testReportAuthorCanReadReportButNotEditScript() throws IOException {
        JsonObject definition = getJsonAs(REPORT_AUTHOR_USER, "/bin/groovyconsole/reports.json?name=" + REPORT_NAME);

        assertTrue(definition.get("canEdit").getAsBoolean(), "report-authors must be able to edit metadata");
        assertFalse(definition.get("canEditScript").getAsBoolean(),
                "report-authors must not be able to edit scripts (not in allowedGroups)");
    }

    @Test
    void testReportAuthorCanEditMetadataDirectly() throws IOException {
        int status = postFormAs(REPORT_AUTHOR_USER, REPORT_PATH,
                new BasicNameValuePair("jcr:title", "Edited by report-author"));

        assertEquals(200, status, "report-authors must be able to modify the report node's own properties");
    }

    @Test
    void testReportAuthorCannotEditGroovyScriptDirectly() throws IOException {
        // the README's ACL backstop: a deny ACE with a rep:glob(*.groovy*) restriction blocks writes to the
        // script's jcr:content, wherever a .groovy file lives in the tree — regardless of how the write is
        // attempted (here, directly through the OOTB SlingPostServlet, bypassing the reports servlet entirely)
        int status = postFormAs(REPORT_AUTHOR_USER, SCRIPT_NODE_PATH,
                new BasicNameValuePair("jcr:data", "throw new RuntimeException('should never run')"));

        assertTrue(status >= 400,
                "report-authors must be denied writing the report's .groovy script content but got " + status);
    }

    @Test
    void testReportAuthorCannotAddChildNodesDirectly() throws IOException {
        // report-authors are not granted jcr:addChildNodes, so they cannot add a replacement script node either
        int status = postFormAs(REPORT_AUTHOR_USER, REPORT_PATH + "/rogue-child",
                new BasicNameValuePair("jcr:primaryType", "nt:unstructured"));

        assertTrue(status >= 400,
                "report-authors must be denied adding child nodes under the reports tree but got " + status);
    }

    @Test
    void testReportAuthorCanEditMetadataViaReportsServlet() throws IOException {
        JsonObject body = new JsonObject();
        body.addProperty("name", REPORT_NAME);
        body.addProperty("title", "Edited via servlet by report-author");
        // submitting a different script is harmless: the servlet falls back to a metadata-only update for
        // callers without console permission, and never writes the script nodes
        body.addProperty("script", "throw new RuntimeException('should never be persisted')");

        int status = postJsonAs(REPORT_AUTHOR_USER, "/bin/groovyconsole/reports", body.toString());
        assertEquals(200, status, "report-authors must be able to save metadata through the reports servlet");

        String persistedScript = getReportScriptData();
        assertEquals(ORIGINAL_SCRIPT, persistedScript,
                "a metadata-only save must never persist a submitted script change");
    }

    @Test
    void testReportAuthorCannotCreateReportViaReportsServlet() throws IOException {
        // creating a report establishes a script, so it requires console permission — which report-authors lack
        JsonObject body = new JsonObject();
        body.addProperty("name", "it-acl-report-author-created");
        body.addProperty("title", "Should not be created");
        body.addProperty("script", "report.data()");

        int status = postJsonAs(REPORT_AUTHOR_USER, "/bin/groovyconsole/reports", body.toString());

        assertEquals(403, status, "report-authors must be denied creating a report through the reports servlet");
    }

    // report-admins: full write access, both directly and through the servlet

    @Test
    void testReportAdminCanReadAndEditScript() throws IOException {
        JsonObject definition = getJsonAs(REPORT_ADMIN_USER, "/bin/groovyconsole/reports.json?name=" + REPORT_NAME);

        assertTrue(definition.get("canEdit").getAsBoolean(), "report-admins must be able to edit metadata");
        assertTrue(definition.get("canEditScript").getAsBoolean(), "report-admins must be able to edit scripts");
    }

    @Test
    void testReportAdminCanEditGroovyScriptDirectly() throws IOException {
        int status = postFormAs(REPORT_ADMIN_USER, SCRIPT_NODE_PATH,
                new BasicNameValuePair("jcr:data", ORIGINAL_SCRIPT));

        assertEquals(200, status, "report-admins must be able to modify the report's .groovy script content");
    }

    @Test
    void testReportAdminCanAddChildNodesDirectly() throws IOException {
        // 201 (created) on a fresh instance, or 200 (updated) if a prior local run already created the node
        int status = postFormAs(REPORT_ADMIN_USER, REPORT_PATH + "/admin-child",
                new BasicNameValuePair("jcr:primaryType", "nt:unstructured"));

        assertTrue(status == 200 || status == 201,
                "report-admins must be able to add child nodes under the reports tree but got " + status);
    }

    @Test
    void testReportAdminCanEditScriptViaReportsServlet() throws IOException {
        String updatedScript = ORIGINAL_SCRIPT + "\n// touched by report-admin";

        JsonObject body = new JsonObject();
        body.addProperty("name", REPORT_NAME);
        body.addProperty("title", "Edited via servlet by report-admin");
        body.addProperty("script", updatedScript);

        int status = postJsonAs(REPORT_ADMIN_USER, "/bin/groovyconsole/reports", body.toString());
        assertEquals(200, status, "report-admins must be able to save the script through the reports servlet");

        assertEquals(updatedScript, getReportScriptData(),
                "report-admins' script edit through the servlet must be persisted");

        // restore the original script so the test is repeatable across local runs
        postFormAs(REPORT_ADMIN_USER, SCRIPT_NODE_PATH, new BasicNameValuePair("jcr:data", ORIGINAL_SCRIPT));
    }

    @Test
    void testReportAdminCanCreateReportViaReportsServlet() throws IOException {
        JsonObject body = new JsonObject();
        body.addProperty("name", "it-acl-report-admin-created");
        body.addProperty("title", "Created by report-admin");
        body.addProperty("script", "report.data()");

        int status = postJsonAs(REPORT_ADMIN_USER, "/bin/groovyconsole/reports", body.toString());

        assertEquals(200, status, "report-admins must be able to create a report through the reports servlet");
    }

    // internals

    private static boolean isReportsApiReady() {
        try {
            HttpGet get = new HttpGet(BASE_URL + "/bin/groovyconsole/reports.json");
            get.addHeader("Authorization", ADMIN_AUTH_HEADER);
            try (CloseableHttpResponse response = httpClient.execute(get)) {
                return response.getStatusLine().getStatusCode() == 200;
            }
        } catch (Exception e) {
            return false;
        }
    }

    private static void createReport() throws IOException {
        JsonObject definition = new JsonObject();
        definition.addProperty("name", REPORT_NAME);
        definition.addProperty("title", "IT ACL Report");
        definition.addProperty("script", ORIGINAL_SCRIPT);

        HttpPost post = new HttpPost(BASE_URL + "/bin/groovyconsole/reports");
        post.setEntity(new StringEntity(definition.toString(), ContentType.APPLICATION_JSON));
        post.addHeader("Authorization", ADMIN_AUTH_HEADER);

        try (CloseableHttpResponse response = httpClient.execute(post)) {
            assertEquals(200, response.getStatusLine().getStatusCode(),
                    "Could not create ACL test report: " + response.getStatusLine());
        }
    }

    /** Reads the report's persisted script content directly (as admin) to verify what actually got written. */
    private static String getReportScriptData() throws IOException {
        // the .groovy node is a plain nt:file; requesting it (without a jcr:content/.json selector) streams the
        // binary jcr:data content directly, which is exactly the script source
        HttpGet get = new HttpGet(BASE_URL + REPORT_PATH + "/" + REPORT_NAME + ".groovy");
        get.addHeader("Authorization", ADMIN_AUTH_HEADER);

        try (CloseableHttpResponse response = httpClient.execute(get)) {
            assertEquals(200, response.getStatusLine().getStatusCode());

            return EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
        }
    }

    private static JsonObject getJsonAs(String user, String path) throws IOException {
        HttpGet get = new HttpGet(BASE_URL + path);
        get.addHeader("Authorization", basicAuthHeader(user));

        try (CloseableHttpResponse response = httpClient.execute(get)) {
            assertEquals(200, response.getStatusLine().getStatusCode(), "Unexpected status for " + path);

            String body = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);

            return JsonParser.parseString(body).getAsJsonObject();
        }
    }

    private static int postFormAs(String user, String path, BasicNameValuePair... params) throws IOException {
        HttpPost post = new HttpPost(BASE_URL + path);
        post.addHeader("Authorization", basicAuthHeader(user));
        post.setEntity(new UrlEncodedFormEntity(Arrays.asList(params), StandardCharsets.UTF_8));

        try (CloseableHttpResponse response = httpClient.execute(post)) {
            EntityUtils.consume(response.getEntity());
            return response.getStatusLine().getStatusCode();
        }
    }

    private static int postJsonAs(String user, String path, String json) throws IOException {
        HttpPost post = new HttpPost(BASE_URL + path);
        post.addHeader("Authorization", basicAuthHeader(user));
        post.setEntity(new StringEntity(json, ContentType.APPLICATION_JSON));

        try (CloseableHttpResponse response = httpClient.execute(post)) {
            EntityUtils.consume(response.getEntity());
            return response.getStatusLine().getStatusCode();
        }
    }

    private static String basicAuthHeader(String user) {
        return "Basic " + Base64.encodeBase64String((user + ":" + user).getBytes(StandardCharsets.UTF_8));
    }
}
