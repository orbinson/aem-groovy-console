package be.orbinson.aem.groovy.console.it;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import org.apache.commons.codec.binary.Base64;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the Groovy Console reports extension, running against a plain Sling Starter with the
 * Groovy Console and the reports content packages installed.  XLSX export is backed by the ServiceMix POI
 * bundles included in the feature aggregate.
 */
class GroovyConsoleReportsIT {

    private static final int SLING_PORT = Integer.getInteger("HTTP_PORT", 8080);
    private static final String BASE_URL = "http://localhost:" + SLING_PORT;
    private static final String AUTH_HEADER = "Basic " + Base64.encodeBase64String("admin:admin".getBytes(StandardCharsets.UTF_8));

    private static final String REPORT_NAME = "it-report";

    private static final String REPORT_SCRIPT = String.join("\n",
            "import be.orbinson.aem.groovy.console.reports.data.ReportColumnType",
            "",
            "def data = report.data()",
            "",
            "println \"generating rows for ${params.greeting}\"",
            "",
            "data.column('Name')",
            "data.column('Count', ReportColumnType.NUMBER)",
            "data.column('Active', ReportColumnType.BOOLEAN)",
            "data.column('Page', ReportColumnType.LINK)",
            "data.column('Note', ReportColumnType.STRING, false)", // UI-only: excluded from exports
            "",
            "(1..3).each { i ->",
            "    data.row(\"${params.greeting}-${i}\", i * 10, i % 2 == 0, [text: \"row-${i}\", href: \"/content/row-${i}\"], \"note-${i}\")",
            "}",
            "",
            "data");

    private static CloseableHttpClient httpClient;

    private static String executionId;

    @BeforeAll
    static void setUp() throws IOException {
        httpClient = HttpClients.createDefault();

        // Wait until the Groovy Console and reports servlets are registered and responding (all bundles are in
        // the launch feature, so there is no post-startup content-package install cascade to wait out).
        await().atMost(180, TimeUnit.SECONDS)
                .pollInterval(5, TimeUnit.SECONDS)
                .untilAsserted(() -> assertTrue(isReportsApiReady(), "Reports API not ready"));
    }

    @AfterAll
    static void tearDown() throws IOException {
        if (httpClient != null) {
            httpClient.close();
        }
    }

    @Test
    void testCreateAndGetReport() throws Exception {
        createReport();

        JsonObject definition = doGet("/bin/groovyconsole/reports.json?name=" + REPORT_NAME, 200);

        assertEquals(REPORT_NAME, definition.get("name").getAsString());
        assertEquals("IT Report", definition.get("title").getAsString());
        assertTrue(definition.get("canEdit").getAsBoolean());
        // admin has console permission, so may edit the executable Groovy too
        assertTrue(definition.get("canEditScript").getAsBoolean());

        JsonArray parameters = definition.getAsJsonArray("parameters");
        assertEquals(2, parameters.size());
        assertEquals("greeting", parameters.get(0).getAsJsonObject().get("name").getAsString());

        // the PATH parameter's browser configuration round-trips through persistence
        JsonObject pathParameter = parameters.get(1).getAsJsonObject();
        assertEquals("root", pathParameter.get("name").getAsString());
        assertEquals("PATH", pathParameter.get("type").getAsString());
        assertEquals("PAGE", pathParameter.get("pathType").getAsString());
        assertEquals("/content", pathParameter.get("rootPath").getAsString());

        // export formats are API-driven from the registered exporters
        assertTrue(containsFormat(definition.getAsJsonArray("exportFormats"), "csv"),
                "Expected csv export format");
    }

    @Test
    void testBrowseListsSelectableChildren() throws Exception {
        JsonObject result = doGet("/bin/groovyconsole/reports/browse.json?path=/&type=NODE", 200);

        JsonArray children = result.getAsJsonArray("children");
        assertTrue(children.size() > 0, "Expected children at the repository root");

        boolean allSelectable = true;
        boolean repNodeVisible = false;
        boolean contentVisible = false;

        for (JsonElement element : children) {
            JsonObject child = element.getAsJsonObject();
            String name = child.get("name").getAsString();

            if (!child.get("selectable").getAsBoolean()) {
                allSelectable = false;
            }
            if (name.startsWith("rep:")) {
                repNodeVisible = true;
            }
            if ("content".equals(name)) {
                contentVisible = true;
                assertTrue(child.get("hasChildren").getAsBoolean(), "Expected /content to report children");
            }
        }

        assertTrue(allSelectable, "Every NODE child should be selectable");
        assertFalse(repNodeVisible, "rep:* nodes must be hidden from the browser");
        assertTrue(contentVisible, "Expected /content under the root");
    }

    @Test
    void testBrowsePageTypeMarksNonPagesUnselectable() throws Exception {
        // on plain Sling there are no cq:Page nodes, so folders are shown for navigation but not selectable
        JsonObject result = doGet("/bin/groovyconsole/reports/browse.json?path=/content&type=PAGE", 200);

        for (JsonElement element : result.getAsJsonArray("children")) {
            JsonObject child = element.getAsJsonObject();
            assertFalse(child.get("selectable").getAsBoolean(),
                    "Folders must not be selectable in the PAGE browser: " + child.get("name").getAsString());
        }
    }

    @Test
    void testReportListedForAdmin() throws Exception {
        createReport();

        JsonObject list = doGet("/bin/groovyconsole/reports.json", 200);

        assertTrue(list.get("canManage").getAsBoolean());

        boolean found = false;
        for (int i = 0; i < list.getAsJsonArray("reports").size(); i++) {
            JsonObject report = list.getAsJsonArray("reports").get(i).getAsJsonObject();
            if (REPORT_NAME.equals(report.get("name").getAsString())) {
                found = true;
            }
        }

        assertTrue(found, "Expected report in list");
    }

    @Test
    void testExecuteReturnsSuccess() throws Exception {
        String id = ensureExecuted();

        JsonObject execution = doGet("/bin/groovyconsole/reports/execution.json?executionId=" + id, 200);

        assertEquals("SUCCESS", execution.get("status").getAsString());
        assertEquals(REPORT_NAME, execution.get("reportName").getAsString());
        assertEquals("admin", execution.get("userId").getAsString());
        assertEquals(3, execution.get("rowCount").getAsLong());
        assertEquals(5, execution.get("columnCount").getAsLong());
        assertEquals("hi", execution.getAsJsonObject("parameterValues").get("greeting").getAsString());
        assertTrue(execution.get("output").getAsString().contains("generating rows for hi"),
                "Expected captured script output on the execution");
    }

    @Test
    void testResultPagination() throws Exception {
        String id = ensureExecuted();

        JsonObject page1 = doGet("/bin/groovyconsole/reports/result.json?executionId=" + id
                + "&page=1&pageSize=2", 200);

        assertEquals(2, page1.getAsJsonArray("rows").size());
        assertEquals(3, page1.get("totalRows").getAsLong());
        assertEquals(2, page1.get("totalPages").getAsInt());
        assertEquals(2, page1.get("nextPage").getAsInt());
        assertEquals(-1, page1.get("previousPage").getAsInt());

        // typed columns round-trip from the script to the persisted result
        JsonArray columns = page1.getAsJsonArray("columns");
        assertEquals("NUMBER", columns.get(1).getAsJsonObject().get("type").getAsString());
        assertEquals("BOOLEAN", columns.get(2).getAsJsonObject().get("type").getAsString());
        assertEquals("LINK", columns.get(3).getAsJsonObject().get("type").getAsString());

        // the UI-only column is present in the result (shown in the table) but flagged not exported
        assertEquals(5, columns.size());
        assertEquals("Note", columns.get(4).getAsJsonObject().get("name").getAsString());
        assertFalse(columns.get(4).getAsJsonObject().get("exported").getAsBoolean());

        JsonArray firstRow = page1.getAsJsonArray("rows").get(0).getAsJsonArray();
        assertEquals("hi-1", firstRow.get(0).getAsString());
        assertEquals(10, firstRow.get(1).getAsInt());
        assertFalse(firstRow.get(2).getAsBoolean());
        assertEquals("/content/row-1", firstRow.get(3).getAsJsonObject().get("href").getAsString());

        JsonObject page2 = doGet("/bin/groovyconsole/reports/result.json?executionId=" + id
                + "&page=2&pageSize=2", 200);

        assertEquals(1, page2.getAsJsonArray("rows").size());
        assertEquals(-1, page2.get("nextPage").getAsInt());
        assertEquals(1, page2.get("previousPage").getAsInt());
    }

    @Test
    void testFormatsListsCsvAndXlsx() throws Exception {
        JsonObject formats = doGet("/bin/groovyconsole/reports/formats.json", 200);

        // csv ships with the reports bundle; xlsx proves the ServiceMix POI wiring on plain Sling
        assertTrue(containsFormat(formats.getAsJsonArray("formats"), "csv"), "Expected csv format");
        assertTrue(containsFormat(formats.getAsJsonArray("formats"), "xlsx"), "Expected xlsx format");
    }

    @Test
    void testCsvExport() throws Exception {
        String id = ensureExecuted();

        HttpGet get = new HttpGet(BASE_URL + "/bin/groovyconsole/reports/export?executionId=" + id
                + "&format=csv");
        get.addHeader("Authorization", AUTH_HEADER);

        try (CloseableHttpResponse response = httpClient.execute(get)) {
            assertEquals(200, response.getStatusLine().getStatusCode());
            assertTrue(response.getFirstHeader("Content-Type").getValue().startsWith("text/csv"));
            assertTrue(response.getFirstHeader("Content-Disposition").getValue().contains(REPORT_NAME));

            String body = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);

            assertTrue(body.contains("Name,Count,Active,Page"), "Expected CSV header row");
            assertTrue(body.contains("hi-1,10,false,"), "Expected first CSV data row");

            // the UI-only 'Note' column (exported = false) must not appear in the export
            assertFalse(body.contains("Note"), "UI-only column header must be excluded from CSV");
            assertFalse(body.contains("note-1"), "UI-only column values must be excluded from CSV");
        }
    }

    @Test
    void testXlsxExport() throws Exception {
        String id = ensureExecuted();

        HttpGet get = new HttpGet(BASE_URL + "/bin/groovyconsole/reports/export?executionId=" + id
                + "&format=xlsx");
        get.addHeader("Authorization", AUTH_HEADER);

        try (CloseableHttpResponse response = httpClient.execute(get)) {
            assertEquals(200, response.getStatusLine().getStatusCode());
            assertEquals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    response.getFirstHeader("Content-Type").getValue().split(";")[0]);

            byte[] body = EntityUtils.toByteArray(response.getEntity());

            assertTrue(body.length > 1000, "Expected non-trivial xlsx body");
            // xlsx files are zip archives
            assertEquals('P', body[0]);
            assertEquals('K', body[1]);
        }
    }

    @Test
    void testUnknownExportFormat() throws Exception {
        String id = ensureExecuted();

        doGet("/bin/groovyconsole/reports/export?executionId=" + id + "&format=nope", 400);
    }

    @Test
    void testCsvExportLocalizesNumbersAndAbsolutizesLinks() throws Exception {
        createReport("it-export-locale", String.join("\n",
                "import be.orbinson.aem.groovy.console.reports.data.ReportColumnType",
                "def data = report.data()",
                "data.column('Amount', ReportColumnType.NUMBER)",
                "data.column('Page', ReportColumnType.LINK)",
                "data.column('External', ReportColumnType.LINK)",
                "data.row(1234.5, [text: 'Edit', href: '/editor.html/content/e2e.html'], "
                        + "[text: 'Ext', href: 'https://example.com/ext'])",
                "data"));

        String id = execute("it-export-locale", new JsonObject()).get("executionId").getAsString();

        // Dutch locale: ';' delimiter, comma decimal
        String nl = exportCsv(id, "nl-NL");
        assertTrue(nl.contains("Amount;Page;External"), "comma-decimal locale uses a ';' delimiter: " + nl);
        assertTrue(nl.contains("1234,5"), "NUMBER formatted with the locale decimal separator: " + nl);
        // relative LINK href absolutized to the request host; already-absolute href preserved
        assertTrue(nl.contains(BASE_URL + "/editor.html/content/e2e.html"),
                "relative LINK href must be absolutized to the request host: " + nl);
        assertTrue(nl.contains("https://example.com/ext"), "absolute LINK href must be preserved: " + nl);

        // English locale: ',' delimiter, dot decimal
        String en = exportCsv(id, "en-US");
        assertTrue(en.contains("Amount,Page,External"), "dot-decimal locale uses a ',' delimiter: " + en);
        assertTrue(en.contains("1234.5"), "NUMBER keeps a dot decimal for a dot-decimal locale: " + en);
        assertTrue(en.contains(BASE_URL + "/editor.html/content/e2e.html"), "relative LINK absolutized: " + en);
    }

    private static String exportCsv(String executionId, String acceptLanguage) throws IOException {
        HttpGet get = new HttpGet(BASE_URL + "/bin/groovyconsole/reports/export?executionId=" + executionId
                + "&format=csv");
        get.addHeader("Authorization", AUTH_HEADER);
        get.addHeader("Accept-Language", acceptLanguage);

        try (CloseableHttpResponse response = httpClient.execute(get)) {
            assertEquals(200, response.getStatusLine().getStatusCode());

            return EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void testMissingRequiredParameter() throws Exception {
        createReport();

        JsonObject body = new JsonObject();
        body.addProperty("name", REPORT_NAME);
        body.add("parameters", new JsonObject());

        try (CloseableHttpResponse response = doPostJson("/bin/groovyconsole/reports/execute", body.toString())) {
            assertEquals(400, response.getStatusLine().getStatusCode());
        }
    }

    @Test
    void testTableScriptCompatibility() throws Exception {
        // reports built on the console's untyped Table still work; columns fall back to STRING
        JsonObject definition = new JsonObject();
        definition.addProperty("name", "it-table-report");
        definition.addProperty("title", "IT Table Report");
        definition.addProperty("script", String.join("\n",
                "def t = report.table()",
                "t.columns('Key', 'Value')",
                "t.row('foo', '1')",
                "t.row('bar', '2')",
                "t"));

        try (CloseableHttpResponse response = doPostJson("/bin/groovyconsole/reports", definition.toString())) {
            assertEquals(200, response.getStatusLine().getStatusCode());
        }

        JsonObject execution = execute("it-table-report", new JsonObject());

        assertEquals("SUCCESS", execution.get("status").getAsString());
        assertEquals(2, execution.get("rowCount").getAsLong());

        JsonObject page = doGet("/bin/groovyconsole/reports/result.json?executionId="
                + execution.get("executionId").getAsString(), 200);

        assertEquals("STRING", page.getAsJsonArray("columns").get(0).getAsJsonObject().get("type").getAsString());
        assertEquals("foo", page.getAsJsonArray("rows").get(0).getAsJsonArray().get(0).getAsString());
    }

    @Test
    void testExecutionsListAndDelete() throws Exception {
        createReport();

        // dedicated execution so deleting it does not interfere with other tests
        String id = executeReport();

        JsonObject executions = doGet("/bin/groovyconsole/reports/executions.json?name=" + REPORT_NAME, 200);
        assertFalse(executions.getAsJsonArray("executions").isEmpty(), "Expected at least one execution");

        HttpDelete delete = new HttpDelete(BASE_URL + "/bin/groovyconsole/reports/executions?executionId=" + id);
        delete.addHeader("Authorization", AUTH_HEADER);

        try (CloseableHttpResponse response = httpClient.execute(delete)) {
            assertEquals(200, response.getStatusLine().getStatusCode());
        }

        doGet("/bin/groovyconsole/reports/execution.json?executionId=" + id, 404);
    }

    @Test
    void testAnonymousAccessDenied() throws Exception {
        createReport();

        // view definition — under pure JCR-ACL access, an anonymous user without read access gets a denial:
        // 401/403, or 404 (the report node is simply not visible to them)
        HttpGet get = new HttpGet(BASE_URL + "/bin/groovyconsole/reports.json?name=" + REPORT_NAME);
        try (CloseableHttpResponse response = httpClient.execute(get)) {
            int status = response.getStatusLine().getStatusCode();
            assertTrue(status >= 400, "Expected error status for anonymous view but got " + status);
        }

        // run report
        JsonObject body = new JsonObject();
        body.addProperty("name", REPORT_NAME);

        HttpPost post = new HttpPost(BASE_URL + "/bin/groovyconsole/reports/execute");
        post.setEntity(new StringEntity(body.toString(), ContentType.APPLICATION_JSON));
        try (CloseableHttpResponse response = httpClient.execute(post)) {
            int status = response.getStatusLine().getStatusCode();
            assertTrue(status >= 400, "Expected error status for anonymous execute but got " + status);
        }

        // edit report
        HttpPost edit = new HttpPost(BASE_URL + "/bin/groovyconsole/reports");
        edit.setEntity(new StringEntity(body.toString(), ContentType.APPLICATION_JSON));
        try (CloseableHttpResponse response = httpClient.execute(edit)) {
            int status = response.getStatusLine().getStatusCode();
            assertTrue(status >= 400, "Expected error status for anonymous edit but got " + status);
        }
    }

    @Test
    void testReportsPageServed() throws Exception {
        // the business UI shell is served by the reports bundle's page servlet (not JCR content)
        HttpGet get = new HttpGet(BASE_URL + "/apps/groovyconsole/reports.html");
        get.addHeader("Authorization", AUTH_HEADER);

        try (CloseableHttpResponse response = httpClient.execute(get)) {
            assertEquals(200, response.getStatusLine().getStatusCode());
            assertTrue(response.getFirstHeader("Content-Type").getValue().startsWith("text/html"));

            String body = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
            assertTrue(body.contains("<gcr-app>"), "Expected the reports app element");
            // the entry is content-hashed (reports-<hash>.js), resolved from the Vite manifest for cache-busting
            assertTrue(body.matches("(?s).*/apps/groovyconsole-reports/spa/assets/reports-[\\w-]+\\.js.*"),
                    "Expected the hashed reports bundle script");
        }
    }

    @Test
    void testConsoleAnnouncesReportsUiExtension() throws Exception {
        // the reports bundle registers a ConsoleUiExtensionProvider; the modern console page must
        // announce its panel module in the injected config
        HttpGet get = new HttpGet(BASE_URL + "/apps/groovyconsole.modern.html");
        get.addHeader("Authorization", AUTH_HEADER);

        try (CloseableHttpResponse response = httpClient.execute(get)) {
            assertEquals(200, response.getStatusLine().getStatusCode());

            String body = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
            assertTrue(body.contains("/apps/groovyconsole-reports/spa/assets/reports-panel.js"),
                    "Expected the reports panel module in the console UI extensions");
        }
    }

    @Test
    void testDeleteReport() throws Exception {
        // dedicated report so deletion does not interfere with other tests
        JsonObject definition = new JsonObject();
        definition.addProperty("name", "it-delete-report");
        definition.addProperty("title", "IT Delete Report");
        definition.addProperty("script", "report.data()");

        try (CloseableHttpResponse response = doPostJson("/bin/groovyconsole/reports", definition.toString())) {
            assertEquals(200, response.getStatusLine().getStatusCode());
        }

        HttpDelete delete = new HttpDelete(BASE_URL + "/bin/groovyconsole/reports?name=it-delete-report");
        delete.addHeader("Authorization", AUTH_HEADER);

        try (CloseableHttpResponse response = httpClient.execute(delete)) {
            assertEquals(200, response.getStatusLine().getStatusCode());
        }

        doGet("/bin/groovyconsole/reports.json?name=it-delete-report", 404);
    }

    @Test
    void testDistributorsListed() throws Exception {
        JsonObject result = doGet("/bin/groovyconsole/reports/distributors.json", 200);

        JsonArray distributors = result.getAsJsonArray("distributors");
        // only usable distributors are listed: on a plain Sling instance the filesystem distributor is disabled
        // by default and the email distributor has no mail service, so neither is offered as a destination
        assertFalse(containsId(distributors, "filesystem"), "the disabled filesystem distributor must not be listed");
        assertFalse(containsId(distributors, "email"), "the email distributor is unavailable without a mail service");

        // the endpoint still surfaces the available export formats used to render a distribution
        assertTrue(containsFormat(result.getAsJsonArray("formats"), "csv"), "Expected csv format");
    }

    @Test
    void testScheduleAndDistributionRoundTripThenRemoval() throws Exception {
        String name = "it-scheduled";

        JsonObject schedule = new JsonObject();
        schedule.addProperty("enabled", true);
        schedule.addProperty("cronExpression", "0 0 6 * * ?");
        // blank runAs => the report runs as the reports service user; a client-supplied scheduledBy is ignored
        schedule.addProperty("runAs", "");
        schedule.addProperty("scheduledBy", "attacker");
        JsonObject scheduleValues = new JsonObject();
        scheduleValues.addProperty("greeting", "scheduled");
        schedule.add("parameterValues", scheduleValues);

        JsonObject config = new JsonObject();
        config.addProperty("directory", "out");
        JsonObject target = new JsonObject();
        target.addProperty("distributorId", "filesystem");
        target.addProperty("format", "csv");
        target.add("config", config);
        JsonArray distributions = new JsonArray();
        distributions.add(target);

        JsonObject definition = new JsonObject();
        definition.addProperty("name", name);
        definition.addProperty("title", "IT Scheduled");
        definition.addProperty("script", "def d = report.data(); d.column('A'); d.row('x'); d");
        definition.add("parameters", new JsonArray());
        definition.add("schedule", schedule);
        definition.add("distributions", distributions);

        try (CloseableHttpResponse response = doPostJson("/bin/groovyconsole/reports", definition.toString())) {
            assertEquals(200, response.getStatusLine().getStatusCode(),
                    "Could not save scheduled report: " + response.getStatusLine());
        }

        JsonObject saved = doGet("/bin/groovyconsole/reports.json?name=" + name, 200);

        JsonObject savedSchedule = saved.getAsJsonObject("schedule");
        assertTrue(savedSchedule.get("enabled").getAsBoolean());
        assertEquals("0 0 6 * * ?", savedSchedule.get("cronExpression").getAsString());
        // scheduledBy is set server-side to the requesting user, never the client-supplied value
        assertEquals("admin", savedSchedule.get("scheduledBy").getAsString());
        assertEquals("scheduled", savedSchedule.getAsJsonObject("parameterValues").get("greeting").getAsString());

        JsonArray savedDistributions = saved.getAsJsonArray("distributions");
        assertEquals(1, savedDistributions.size());
        assertEquals("filesystem", savedDistributions.get(0).getAsJsonObject().get("distributorId").getAsString());
        assertEquals("csv", savedDistributions.get(0).getAsJsonObject().get("format").getAsString());
        assertEquals("out", savedDistributions.get(0).getAsJsonObject().getAsJsonObject("config")
                .get("directory").getAsString());

        // re-save without a schedule/distributions: both must be removed (and the cron job unscheduled)
        JsonObject withoutSchedule = new JsonObject();
        withoutSchedule.addProperty("name", name);
        withoutSchedule.addProperty("title", "IT Scheduled");
        withoutSchedule.addProperty("script", "def d = report.data(); d.column('A'); d.row('x'); d");
        withoutSchedule.add("parameters", new JsonArray());

        try (CloseableHttpResponse response = doPostJson("/bin/groovyconsole/reports", withoutSchedule.toString())) {
            assertEquals(200, response.getStatusLine().getStatusCode());
        }

        JsonObject cleared = doGet("/bin/groovyconsole/reports.json?name=" + name, 200);
        assertTrue(cleared.get("schedule").isJsonNull(), "schedule must be removed when omitted");
        assertEquals(0, cleared.getAsJsonArray("distributions").size(), "distributions must be removed when omitted");
    }

    @Test
    void testScheduleWithInvalidCronRejected() throws Exception {
        String name = "it-bad-cron";

        JsonObject schedule = new JsonObject();
        schedule.addProperty("enabled", true);
        // sub-minute schedule: rejected before anything is persisted
        schedule.addProperty("cronExpression", "* * * * * ?");

        JsonObject definition = new JsonObject();
        definition.addProperty("name", name);
        definition.addProperty("title", "Bad Cron");
        definition.addProperty("script", "report.data()");
        definition.add("parameters", new JsonArray());
        definition.add("schedule", schedule);

        try (CloseableHttpResponse response = doPostJson("/bin/groovyconsole/reports", definition.toString())) {
            assertEquals(400, response.getStatusLine().getStatusCode(),
                    "sub-minute cron must be rejected: " + response.getStatusLine());
        }

        // nothing should have been created
        doGet("/bin/groovyconsole/reports.json?name=" + name, 404);
    }

    @Test
    void testManualDistributeToDisabledFilesystemRecordsError() throws Exception {
        createReport("it-distribute", "def d = report.data(); d.column('A'); d.row('x'); d");
        String id = execute("it-distribute", new JsonObject()).get("executionId").getAsString();

        JsonObject config = new JsonObject();
        config.addProperty("directory", "out");
        JsonObject target = new JsonObject();
        target.addProperty("distributorId", "filesystem");
        target.addProperty("format", "csv");
        target.add("config", config);
        JsonArray targets = new JsonArray();
        targets.add(target);

        JsonObject body = new JsonObject();
        body.addProperty("executionId", id);
        body.add("targets", targets);

        // the filesystem distributor is disabled by default, so distribution fails but the run itself does not:
        // the endpoint returns 200 and records the failure on the execution
        try (CloseableHttpResponse response = doPostJson("/bin/groovyconsole/reports/distribute", body.toString())) {
            assertEquals(200, response.getStatusLine().getStatusCode());

            JsonObject execution = parse(EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8));
            JsonArray errors = execution.getAsJsonArray("distributionErrors");

            assertNotNull(errors, "Expected distribution errors to be recorded");
            assertFalse(errors.isEmpty(), "Expected a recorded distribution failure");
            assertTrue(errors.get(0).getAsString().toLowerCase().contains("disabled"),
                    "Expected the disabled-distributor error: " + errors);
        }
    }

    @Test
    void testMultiValueParameterRoundTripsAndExecutesAsList() throws Exception {
        JsonObject multi = new JsonObject();
        multi.addProperty("name", "items");
        multi.addProperty("label", "Items");
        multi.addProperty("type", "STRING");
        multi.addProperty("multiple", true);

        JsonArray parameters = new JsonArray();
        parameters.add(multi);

        createReport("it-multi", String.join("\n",
                "def data = report.data()",
                "data.column('Item')",
                "(params.items ?: []).each { data.row(it as String) }",
                "data"), parameters);

        // the multiple flag round-trips through persistence
        JsonObject definition = doGet("/bin/groovyconsole/reports.json?name=it-multi", 200);
        assertTrue(definition.getAsJsonArray("parameters").get(0).getAsJsonObject().get("multiple").getAsBoolean(),
                "the multiple flag must round-trip");

        // an array value is passed to the script as a list, one row per element
        JsonObject values = new JsonObject();
        JsonArray items = new JsonArray();
        items.add("alpha");
        items.add("beta");
        items.add("gamma");
        values.add("items", items);

        JsonObject execution = execute("it-multi", values);

        assertEquals("SUCCESS", execution.get("status").getAsString());
        assertEquals(3, execution.get("rowCount").getAsLong(), "each submitted value must become a row");

        JsonObject page = doGet("/bin/groovyconsole/reports/result.json?executionId="
                + execution.get("executionId").getAsString(), 200);
        assertEquals("alpha", page.getAsJsonArray("rows").get(0).getAsJsonArray().get(0).getAsString());
        assertEquals("gamma", page.getAsJsonArray("rows").get(2).getAsJsonArray().get(0).getAsString());
    }

    @Test
    void testDynamicOptionsEndpointReturnsValueLabelPairs() throws Exception {
        JsonObject body = new JsonObject();
        body.addProperty("script", String.join("\n",
                "def options = report.options()",
                "options.add('v1', 'Label One')",
                "options.add('v2')",
                "options"));
        body.add("parameters", new JsonObject());

        try (CloseableHttpResponse response = doPostJson("/bin/groovyconsole/reports/options", body.toString())) {
            assertEquals(200, response.getStatusLine().getStatusCode());

            JsonObject result = parse(EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8));
            JsonArray options = result.getAsJsonArray("options");

            assertEquals(2, options.size());
            assertEquals("v1", options.get(0).getAsJsonObject().get("value").getAsString());
            assertEquals("Label One", options.get(0).getAsJsonObject().get("label").getAsString());
            // a single-arg add uses the value as the label
            assertEquals("v2", options.get(1).getAsJsonObject().get("value").getAsString());
            assertEquals("v2", options.get(1).getAsJsonObject().get("label").getAsString());
        }
    }

    @Test
    void testManualDistributeUnknownExecutionReturns404() throws Exception {
        JsonObject target = new JsonObject();
        target.addProperty("distributorId", "filesystem");
        target.addProperty("format", "csv");
        target.add("config", new JsonObject());
        JsonArray targets = new JsonArray();
        targets.add(target);

        JsonObject body = new JsonObject();
        body.addProperty("executionId", "does-not-exist");
        body.add("targets", targets);

        try (CloseableHttpResponse response = doPostJson("/bin/groovyconsole/reports/distribute", body.toString())) {
            assertEquals(404, response.getStatusLine().getStatusCode());
        }
    }

    @Test
    void testDynamicOptionsEndpointReportsScriptFailure() throws Exception {
        JsonObject body = new JsonObject();
        body.addProperty("script", "throw new RuntimeException('boom from options')");
        body.add("parameters", new JsonObject());

        try (CloseableHttpResponse response = doPostJson("/bin/groovyconsole/reports/options", body.toString())) {
            assertEquals(500, response.getStatusLine().getStatusCode());
            assertTrue(EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8).contains("boom from options"),
                    "a failing options script must surface its error");
        }
    }

    @Test
    void testManualDistributeWithoutTargetsReturns400() throws Exception {
        String id = ensureExecuted();

        JsonObject body = new JsonObject();
        body.addProperty("executionId", id);
        body.add("targets", new JsonArray());

        try (CloseableHttpResponse response = doPostJson("/bin/groovyconsole/reports/distribute", body.toString())) {
            assertEquals(400, response.getStatusLine().getStatusCode());
        }
    }

    @Test
    void testTagBrowseIsSlingSafe() throws Exception {
        // pure-JCR tag browsing: on plain Sling /content/cq:tags does not exist, so the TAG browser returns
        // empty instead of failing — proving the tag picker never touches an AEM API
        JsonObject result = doGet("/bin/groovyconsole/reports/browse.json?path=/content/cq:tags&type=TAG", 200);

        assertEquals(0, result.getAsJsonArray("children").size(), "no tags exist on a plain Sling instance");
    }

    // internals

    private static boolean containsId(JsonArray items, String id) {
        for (int i = 0; i < items.size(); i++) {
            if (id.equals(items.get(i).getAsJsonObject().get("id").getAsString())) {
                return true;
            }
        }

        return false;
    }

    private static boolean isReportsApiReady() {
        try {
            // reports servlets registered
            HttpGet get = new HttpGet(BASE_URL + "/bin/groovyconsole/reports.json");
            get.addHeader("Authorization", AUTH_HEADER);
            try (CloseableHttpResponse response = httpClient.execute(get)) {
                if (response.getStatusLine().getStatusCode() != 200) {
                    return false;
                }
                String body = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                if (!JsonParser.parseString(body).getAsJsonObject().has("reports")) {
                    return false;
                }
            }

            // the console itself is required to execute reports
            HttpPost post = new HttpPost(BASE_URL + "/bin/groovyconsole/post");
            post.setEntity(new StringEntity("script=return 'ready'",
                    ContentType.APPLICATION_FORM_URLENCODED));
            post.addHeader("Authorization", AUTH_HEADER);
            try (CloseableHttpResponse response = httpClient.execute(post)) {
                return response.getStatusLine().getStatusCode() == 200;
            }
        } catch (Exception e) {
            return false;
        }
    }

    private static synchronized void createReport() throws IOException {
        JsonObject parameter = new JsonObject();
        parameter.addProperty("name", "greeting");
        parameter.addProperty("label", "Greeting");
        parameter.addProperty("type", "STRING");
        parameter.addProperty("required", true);

        // a PATH parameter exercising the path-browser configuration round-trip
        JsonObject pathParameter = new JsonObject();
        pathParameter.addProperty("name", "root");
        pathParameter.addProperty("label", "Root");
        pathParameter.addProperty("type", "PATH");
        pathParameter.addProperty("required", false);
        pathParameter.addProperty("pathType", "PAGE");
        pathParameter.addProperty("rootPath", "/content");

        JsonArray parameters = new JsonArray();
        parameters.add(parameter);
        parameters.add(pathParameter);

        JsonObject definition = new JsonObject();
        definition.addProperty("name", REPORT_NAME);
        definition.addProperty("title", "IT Report");
        definition.addProperty("description", "Report used by integration tests");
        definition.addProperty("category", "IT");
        definition.addProperty("script", REPORT_SCRIPT);
        definition.add("parameters", parameters);

        try (CloseableHttpResponse response = doPostJson("/bin/groovyconsole/reports", definition.toString())) {
            assertEquals(200, response.getStatusLine().getStatusCode(),
                    "Could not create report: " + response.getStatusLine());
        }
    }

    @Test
    void testAllResultRowsPersisted() throws Exception {
        createReport("it-allrows", String.join("\n",
                "def data = report.data()",
                "data.column('N')",
                "(1..8).each { data.row(it as String) }",
                "data"));

        JsonObject execution = execute("it-allrows", new JsonObject());

        assertEquals("SUCCESS", execution.get("status").getAsString());
        assertEquals(8, execution.get("rowCount").getAsLong(), "every result row must be persisted");
    }

    @Test
    void testFailedExecutionRecordsStackTrace() throws Exception {
        createReport("it-failing", "throw new RuntimeException('boom from report')");

        JsonObject execution = execute("it-failing", new JsonObject());

        assertEquals("FAILED", execution.get("status").getAsString());
        assertTrue(execution.get("exceptionStackTrace").getAsString().contains("boom from report"),
                "a failed execution must persist the stack trace");
    }

    private static void createReport(String name, String script) throws IOException {
        createReport(name, script, new JsonArray());
    }

    private static void createReport(String name, String script, JsonArray parameters) throws IOException {
        JsonObject definition = new JsonObject();
        definition.addProperty("name", name);
        definition.addProperty("title", name);
        definition.addProperty("script", script);
        definition.add("parameters", parameters);

        try (CloseableHttpResponse response = doPostJson("/bin/groovyconsole/reports", definition.toString())) {
            assertEquals(200, response.getStatusLine().getStatusCode(), "Could not create report " + name);
        }
    }

    private static JsonObject execute(String name, JsonObject parameters) throws IOException {
        JsonObject body = new JsonObject();
        body.addProperty("name", name);
        body.add("parameters", parameters);

        try (CloseableHttpResponse response = doPostJson("/bin/groovyconsole/reports/execute", body.toString())) {
            assertEquals(200, response.getStatusLine().getStatusCode(), "Could not execute report " + name);

            JsonObject execution = parse(EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8));

            // execution is asynchronous: it starts RUNNING, so poll until it reaches a terminal state
            return awaitExecution(execution.get("executionId").getAsString());
        }
    }

    /** Poll an execution until it is no longer RUNNING (reports execute asynchronously). */
    private static JsonObject awaitExecution(String executionId) {
        JsonObject[] holder = new JsonObject[1];

        await().atMost(60, TimeUnit.SECONDS).pollInterval(500, TimeUnit.MILLISECONDS).untilAsserted(() -> {
            holder[0] = doGet("/bin/groovyconsole/reports/execution.json?executionId=" + executionId, 200);
            assertNotEquals("RUNNING", holder[0].get("status").getAsString(), "execution still running");
        });

        return holder[0];
    }

    private static synchronized String ensureExecuted() throws IOException {
        if (executionId == null) {
            createReport();
            executionId = executeReport();
        }

        return executionId;
    }

    private static String executeReport() throws IOException {
        JsonObject parameters = new JsonObject();
        parameters.addProperty("greeting", "hi");

        JsonObject body = new JsonObject();
        body.addProperty("name", REPORT_NAME);
        body.add("parameters", parameters);

        try (CloseableHttpResponse response = doPostJson("/bin/groovyconsole/reports/execute", body.toString())) {
            assertEquals(200, response.getStatusLine().getStatusCode(),
                    "Could not execute report: " + response.getStatusLine());

            JsonObject started = parse(EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8));
            String id = started.get("executionId").getAsString();

            JsonObject execution = awaitExecution(id);
            assertEquals("SUCCESS", execution.get("status").getAsString(), "Execution failed: " + execution);

            return id;
        }
    }

    private static CloseableHttpResponse doPostJson(String path, String json) throws IOException {
        HttpPost post = new HttpPost(BASE_URL + path);
        post.setEntity(new StringEntity(json, ContentType.APPLICATION_JSON));
        post.addHeader("Authorization", AUTH_HEADER);

        return httpClient.execute(post);
    }

    private static JsonObject doGet(String path, int expectedStatus) throws IOException {
        HttpGet get = new HttpGet(BASE_URL + path);
        get.addHeader("Authorization", AUTH_HEADER);

        try (CloseableHttpResponse response = httpClient.execute(get)) {
            assertEquals(expectedStatus, response.getStatusLine().getStatusCode(),
                    "Unexpected status for " + path + ": " + response.getStatusLine());

            String body = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
            return parse(body);
        }
    }

    private static JsonObject parse(String body) {
        try {
            return JsonParser.parseString(body).getAsJsonObject();
        } catch (JsonSyntaxException e) {
            fail("Could not parse response body as JSON: " + body);
            return null;
        }
    }

    private static boolean containsFormat(JsonArray formats, String format) {
        for (int i = 0; i < formats.size(); i++) {
            if (format.equals(formats.get(i).getAsJsonObject().get("format").getAsString())) {
                return true;
            }
        }

        return false;
    }
}
