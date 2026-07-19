package be.orbinson.aem.groovy.console.it;

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
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Exercises the migration extension's JMX MBean over a real JMX (RMI) connection to the launched instance --
 * not the HTTP API (see {@link MigrationIT}) and not unit-test mocks (see
 * {@code DefaultMigrationServiceMBeanTest}). This is the only place that confirms the MBean is actually
 * registered and reachable through the real JMX protocol, including operation-overload resolution
 * ({@code run()}/{@code run(String)}/{@code run(String,String)}) and attribute access, which a raw HTTP call
 * or an in-process mock cannot verify.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MigrationJmxIT {

    private static final int SLING_PORT = Integer.getInteger("HTTP_PORT", 8080);
    private static final int JMX_PORT = Integer.getInteger("JMX_PORT", 7199);
    private static final String BASE_URL = "http://localhost:" + SLING_PORT;
    private static final String AUTH_HEADER = "Basic " + Base64.encodeBase64String("admin:admin".getBytes(StandardCharsets.UTF_8));

    private static final String SCRIPTS_BASE_PATH = "/conf/groovyconsole/scripts/migration";
    private static final String JMX_IT_SUBPATH = SCRIPTS_BASE_PATH + "/jmx-it";

    private static CloseableHttpClient httpClient;
    private static JMXConnector jmxConnector;
    private static MBeanServerConnection connection;
    private static ObjectName migrationMBean;

    @BeforeAll
    static void setUp() throws Exception {
        httpClient = HttpClients.createDefault();
        migrationMBean = new ObjectName("be.orbinson.aem.groovyconsole:type=Migration");

        // same "single successful probe" readiness approach as MigrationIT/GroovyConsoleReportsIT -- all
        // bundles/content are pre-converted into the launch feature (cp-converter), so there is no post-startup
        // content-package install cascade to wait out here.
        await().atMost(180, TimeUnit.SECONDS)
                .pollInterval(5, TimeUnit.SECONDS)
                .untilAsserted(() -> assertTrue(isConsoleReady(), "Groovy Console not ready"));

        ensureJmxItFolderExists();

        JMXServiceURL serviceUrl = new JMXServiceURL(
                "service:jmx:rmi:///jndi/rmi://localhost:" + JMX_PORT + "/jmxrmi");

        // the RMI registry can take a moment to come up after the OSGi framework itself is ready
        await().atMost(60, TimeUnit.SECONDS)
                .pollInterval(2, TimeUnit.SECONDS)
                .untilAsserted(() -> jmxConnector = JMXConnectorFactory.connect(serviceUrl));

        connection = jmxConnector.getMBeanServerConnection();
    }

    @AfterAll
    static void tearDown() throws IOException {
        if (jmxConnector != null) {
            jmxConnector.close();
        }
        if (httpClient != null) {
            httpClient.close();
        }
    }

    @Test
    @Order(1)
    void testMBeanIsRegistered() throws Exception {
        assertTrue(connection.isRegistered(migrationMBean), "Migration MBean not registered : " + migrationMBean);
    }

    @Test
    @Order(2)
    void testRunWithPathOverJmx() throws Exception {
        createMigrationScript("001-jmx.groovy", "println 'migration via jmx'");

        String summary = (String) connection.invoke(migrationMBean, "run",
                new Object[]{JMX_IT_SUBPATH}, new String[]{String.class.getName()});

        assertTrue(summary.contains("SUCCESS"), "Expected SUCCESS in summary : " + summary);
        assertTrue(summary.contains("001-jmx.groovy"), "Expected script path in summary : " + summary);
    }

    @Test
    @Order(3)
    void testRunWithPathAndDataOverJmx() throws Exception {
        createMigrationScript("002-jmx-data.groovy", "println \"received data: \" + data");

        String summary = (String) connection.invoke(migrationMBean, "run",
                new Object[]{JMX_IT_SUBPATH, "hello-from-jmx"},
                new String[]{String.class.getName(), String.class.getName()});

        assertTrue(summary.contains("SUCCESS"), "Expected SUCCESS in summary : " + summary);
        assertTrue(summary.contains("002-jmx-data.groovy"), "Expected script path in summary : " + summary);
    }

    @Test
    @Order(4)
    void testPendingScriptsAttributeOverJmx() throws Exception {
        createMigrationScript("003-pending.groovy", "println 'pending via jmx'");

        @SuppressWarnings("unchecked")
        List<String> pending = (List<String>) connection.getAttribute(migrationMBean, "PendingScripts");

        assertTrue(pending.stream().anyMatch(path -> path.endsWith("003-pending.groovy")),
                "Expected 003-pending.groovy in pending scripts : " + pending);
    }

    @Test
    @Order(5)
    void testIsRunningAttributeOverJmx() throws Exception {
        boolean running = (boolean) connection.getAttribute(migrationMBean, "Running");

        assertFalse(running, "No run should be in progress between test methods");
    }

    @Test
    @Order(6)
    void testGetRunsOperationOverJmx() throws Exception {
        String summary = (String) connection.invoke(migrationMBean, "getRuns",
                new Object[]{5}, new String[]{int.class.getName()});

        assertFalse(summary.isEmpty(), "Expected at least one run in the summary");
        assertTrue(summary.contains("SUCCESS"), "Expected a SUCCESS run in the summary : " + summary);
    }

    private static boolean isConsoleReady() {
        try {
            HttpPost post = new HttpPost(BASE_URL + "/bin/groovyconsole/post");
            List<BasicNameValuePair> params = new ArrayList<>();
            params.add(new BasicNameValuePair("script", "return 'ready'"));
            post.setEntity(new UrlEncodedFormEntity(params, StandardCharsets.UTF_8));
            post.addHeader("Authorization", AUTH_HEADER);

            try (CloseableHttpResponse response = httpClient.execute(post)) {
                return response.getStatusLine().getStatusCode() == 200;
            }
        } catch (Exception e) {
            return false;
        }
    }

    private static void ensureJmxItFolderExists() throws IOException {
        JsonObject response = executeScript(
                "if (!session.nodeExists('" + JMX_IT_SUBPATH + "')) {\n" +
                "    session.getNode('" + SCRIPTS_BASE_PATH + "').addNode('jmx-it', 'sling:Folder')\n" +
                "    session.save()\n" +
                "}"
        );

        assertEquals("", response.get("exceptionStackTrace").getAsString(), "Could not create the jmx-it folder");
    }

    /**
     * Create or replace a migration script file below the dedicated {@code jmx-it} subfolder via the Groovy
     * Console post servlet, so JMX-triggered runs (scoped to that subfolder via {@code path=...}) never
     * interfere with the scripts {@link MigrationIT} creates directly below the scripts base path.
     */
    private static void createMigrationScript(String name, String content) throws IOException {
        String encodedContent = Base64.encodeBase64String(content.getBytes(StandardCharsets.UTF_8));

        JsonObject response = executeScript(
                "def parent = session.getNode('" + JMX_IT_SUBPATH + "')\n" +
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
