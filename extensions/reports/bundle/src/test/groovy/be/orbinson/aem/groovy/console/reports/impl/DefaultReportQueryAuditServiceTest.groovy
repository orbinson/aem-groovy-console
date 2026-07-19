package be.orbinson.aem.groovy.console.reports.impl

import be.orbinson.aem.groovy.console.GroovyConsoleService
import be.orbinson.aem.groovy.console.reports.model.ReportDefinition
import be.orbinson.aem.groovy.console.reports.model.ReportExecutionStatus
import be.orbinson.aem.groovy.console.reports.model.ReportQueryPlan
import be.orbinson.aem.groovy.console.response.RunScriptResponse
import io.wcm.testing.mock.aem.junit5.AemContext
import io.wcm.testing.mock.aem.junit5.AemContextExtension
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

import javax.jcr.Session

import static org.junit.jupiter.api.Assertions.*
import static org.mockito.ArgumentMatchers.any
import static org.mockito.Mockito.doReturn
import static org.mockito.Mockito.mock
import static org.mockito.Mockito.spy
import static org.mockito.Mockito.when

@ExtendWith(AemContextExtension.class)
class DefaultReportQueryAuditServiceTest {

    private final AemContext context = new AemContext()

    private GroovyConsoleService groovyConsoleService = mock(GroovyConsoleService)

    @Test
    void "query audit is unavailable and returns an empty result when the extension is not installed"() {
        context.registerService(GroovyConsoleService, groovyConsoleService)

        // no ReportScriptIndexAuditor registered -> query-audit extension absent
        def service = context.registerInjectActivateService(new DefaultReportQueryAuditService())

        assertFalse(service.available)

        def audit = service.audit(new ReportDefinition(name: "r", script: "def x = 1"), [:],
                context.resourceResolver())

        assertEquals(ReportExecutionStatus.SUCCESS, audit.status)
        assertTrue(audit.queries.isEmpty())
    }

    @Test
    void "audit runs the script through the auditor and maps the reported plans"() {
        context.registerService(GroovyConsoleService, groovyConsoleService)

        def response = mock(RunScriptResponse)
        when(response.output).thenReturn("done")
        when(response.exceptionStackTrace).thenReturn(null)
        when(response.runningTime).thenReturn("0.10s")
        when(groovyConsoleService.runScript(any())).thenReturn(response)

        // the bridge runs the work and reports its plans; stub it to run the work and return two plans
        def auditor = { Session session, Runnable work ->
            work.run()
            [
                    new ReportQueryPlan(statement: "SELECT * FROM [cq:Page]", language: "JCR-SQL2",
                            plan: "[cq:Page] as [a] /* traverse */", needsIndex: true),
                    new ReportQueryPlan(statement: "SELECT * FROM [dam:Asset]", language: "JCR-SQL2",
                            plan: "/* damAssetLucene */", needsIndex: false)
            ]
        } as ReportScriptIndexAuditor
        context.registerService(ReportScriptIndexAuditor, auditor)

        def service = context.registerInjectActivateService(new DefaultReportQueryAuditService())

        assertTrue(service.available)

        // the mock resolver does not support clone(null); make it return itself so audit() can run
        def resolver = spy(context.resourceResolver())
        doReturn(resolver).when(resolver).clone(null)

        def audit = service.audit(new ReportDefinition(name: "r", script: "def x = 1"), [:], resolver)

        assertEquals(ReportExecutionStatus.SUCCESS, audit.status)
        assertEquals("done", audit.output)
        assertEquals(2, audit.queries.size())
        assertTrue(audit.queries[0].needsIndex, "a traversing plan must be flagged")
        assertFalse(audit.queries[1].needsIndex, "an index-backed plan must not be flagged")
        assertEquals("SELECT * FROM [cq:Page]", audit.queries[0].statement)
    }
}
