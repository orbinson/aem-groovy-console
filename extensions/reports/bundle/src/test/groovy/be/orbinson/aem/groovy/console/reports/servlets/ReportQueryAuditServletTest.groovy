package be.orbinson.aem.groovy.console.reports.servlets

import be.orbinson.aem.groovy.console.configuration.ConfigurationService
import be.orbinson.aem.groovy.console.reports.ReportQueryAuditService
import be.orbinson.aem.groovy.console.reports.ReportService
import be.orbinson.aem.groovy.console.reports.model.ReportExecutionStatus
import be.orbinson.aem.groovy.console.reports.model.ReportQueryAudit
import be.orbinson.aem.groovy.console.reports.model.ReportQueryPlan
import groovy.json.JsonBuilder
import groovy.json.JsonSlurper
import io.wcm.testing.mock.aem.junit5.AemContext
import io.wcm.testing.mock.aem.junit5.AemContextExtension
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

import static javax.servlet.http.HttpServletResponse.*
import static org.junit.jupiter.api.Assertions.*
import static org.mockito.ArgumentMatchers.any
import static org.mockito.ArgumentMatchers.anyString
import static org.mockito.Mockito.mock
import static org.mockito.Mockito.when

@ExtendWith(AemContextExtension.class)
class ReportQueryAuditServletTest {

    private final AemContext context = new AemContext()

    ReportQueryAuditServlet servlet

    ReportService reportService

    ReportQueryAuditService queryAuditService

    ConfigurationService configurationService

    @BeforeEach
    void beforeEach() {
        reportService = mock(ReportService)
        queryAuditService = mock(ReportQueryAuditService)
        configurationService = mock(ConfigurationService)

        context.registerService(ReportService, reportService)
        context.registerService(ReportQueryAuditService, queryAuditService)
        context.registerService(ConfigurationService, configurationService)

        servlet = context.registerInjectActivateService(new ReportQueryAuditServlet())
    }

    @Test
    void getWithoutPermissionReturnsForbidden() {
        when(configurationService.hasPermission(any())).thenReturn(false)

        servlet.doGet(context.request(), context.response())

        assertEquals(SC_FORBIDDEN, context.response().status)
    }

    @Test
    void getReportsAvailability() {
        when(configurationService.hasPermission(any())).thenReturn(true)
        when(queryAuditService.isAvailable()).thenReturn(true)

        servlet.doGet(context.request(), context.response())

        assertEquals(SC_OK, context.response().status)
        assertEquals([available: true], new JsonSlurper().parseText(context.response().outputAsString))
    }

    @Test
    void postWithoutPermissionReturnsForbidden() {
        when(configurationService.hasPermission(any())).thenReturn(false)

        servlet.doPost(context.request(), context.response())

        assertEquals(SC_FORBIDDEN, context.response().status)
    }

    @Test
    void postWithoutScriptReturnsBadRequest() {
        when(configurationService.hasPermission(any())).thenReturn(true)
        setBody([name: "my-report"])

        servlet.doPost(context.request(), context.response())

        assertEquals(SC_BAD_REQUEST, context.response().status)
    }

    @Test
    void postForNonEditorReturnsForbidden() {
        when(configurationService.hasPermission(any())).thenReturn(true)
        when(reportService.getReport(any(), anyString())).thenReturn(null)
        when(reportService.canCreate(any())).thenReturn(false)
        setBody([name: "my-report", script: "def x = 1"])

        servlet.doPost(context.request(), context.response())

        assertEquals(SC_FORBIDDEN, context.response().status)
    }

    @Test
    void postReturnsAuditResult() {
        when(configurationService.hasPermission(any())).thenReturn(true)
        when(reportService.getReport(any(), anyString())).thenReturn(null)
        when(reportService.canCreate(any())).thenReturn(true)
        when(queryAuditService.audit(any(), any(), any())).thenReturn(new ReportQueryAudit(
                status: ReportExecutionStatus.SUCCESS,
                queries: [
                        new ReportQueryPlan(statement: "SELECT * FROM [cq:Page]", language: "JCR-SQL2",
                                plan: "[cq:Page] as [a] /* traverse */", needsIndex: true),
                        new ReportQueryPlan(statement: "SELECT * FROM [dam:Asset]", language: "JCR-SQL2",
                                plan: "/* damAssetLucene */", needsIndex: false)
                ],
                output: "done",
                runningTime: "0.10s"))
        setBody([name: "my-report", script: "def x = 1"])

        servlet.doPost(context.request(), context.response())

        assertEquals(SC_OK, context.response().status)

        def json = new JsonSlurper().parseText(context.response().outputAsString)

        assertEquals("SUCCESS", json.status)
        assertEquals(2, json.queries.size())
        assertTrue(json.queries[0].needsIndex as boolean)
        assertFalse(json.queries[1].needsIndex as boolean)
        assertEquals("SELECT * FROM [cq:Page]", json.queries[0].statement)
        assertEquals("JCR-SQL2", json.queries[0].language)
        assertEquals("done", json.output)
    }

    private void setBody(Map body) {
        context.request().content = new JsonBuilder(body).toString().getBytes("UTF-8")
    }
}
