package be.orbinson.aem.groovy.console.reports.impl

import be.orbinson.aem.groovy.console.GroovyConsoleService
import be.orbinson.aem.groovy.console.reports.ReportException
import be.orbinson.aem.groovy.console.reports.ReportQueryAuditService
import be.orbinson.aem.groovy.console.reports.model.ReportDefinition
import be.orbinson.aem.groovy.console.reports.model.ReportExecutionStatus
import be.orbinson.aem.groovy.console.reports.model.ReportQueryAudit
import be.orbinson.aem.groovy.console.response.RunScriptResponse
import groovy.util.logging.Slf4j
import org.apache.sling.api.resource.ResourceResolver
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ReferenceCardinality
import org.osgi.service.component.annotations.ReferencePolicy

import javax.jcr.Session

import static be.orbinson.aem.groovy.console.reports.constants.ReportsConstants.CHARSET

/**
 * Default {@link ReportQueryAuditService}. Runs a report script on a detached resolver (like a preview — nothing is
 * persisted) and, via the optional query-audit extension, reports each executed JCR query's Oak index usage.
 * <p>
 * Self-contained on purpose: it references only the reports-owned {@link ReportScriptIndexAuditor} bridge (never a
 * query-audit type) and holds it optionally, so it activates whether or not the query-audit extension is installed —
 * and {@link DefaultReportExecutionService} carries no query-audit concern at all.
 */
@Component(service = ReportQueryAuditService, immediate = true)
@Slf4j("LOG")
class DefaultReportQueryAuditService implements ReportQueryAuditService {

    @Reference
    private GroovyConsoleService groovyConsoleService

    /**
     * Optional bridge to the query-audit extension. Present (non-null) only when the query-audit bundle is installed —
     * DS registers a {@link ReportScriptIndexAuditor} then; absent otherwise (e.g. on AEM as a Cloud Service). This
     * service references only the {@link ReportScriptIndexAuditor} interface (never the query-audit types), so it
     * always loads and activates whether or not query-audit is installed.
     */
    @Reference(cardinality = ReferenceCardinality.OPTIONAL, policy = ReferencePolicy.DYNAMIC)
    private volatile ReportScriptIndexAuditor scriptIndexAuditor

    @Override
    boolean isAvailable() {
        scriptIndexAuditor != null
    }

    @Override
    ReportQueryAudit audit(ReportDefinition reportDefinition, Map<String, String> parameterValues,
                           ResourceResolver resourceResolver) {
        def auditor = scriptIndexAuditor

        if (!auditor) {
            LOG.warn("query audit requested but the query-audit extension is not installed; skipping")

            return new ReportQueryAudit(status: ReportExecutionStatus.SUCCESS)
        }

        def coercedValues = ParameterCoercer.coerce(reportDefinition.parameters, parameterValues)
        def script = resolveScript(reportDefinition)
        def userId = resourceResolver.userID

        LOG.info("auditing queries for report : {} as user : {}", reportDefinition.name, userId)

        // run on a clone so a script that makes (then fails to commit) transient JCR changes cannot leave them
        // pending on the request-scoped resolver — runScript does not revert on failure (mirrors preview)
        resourceResolver.clone(null).withCloseable { auditResolver ->
            def scriptContext = buildReportContext(reportDefinition.name, coercedValues, script, userId, auditResolver)
            def session = auditResolver.adaptTo(Session)
            def responseHolder = new RunScriptResponse[1]

            def plans = auditor.audit(session,
                    { responseHolder[0] = groovyConsoleService.runScript(scriptContext) } as Runnable)

            def response = responseHolder[0]

            new ReportQueryAudit(
                    status: response.exceptionStackTrace ? ReportExecutionStatus.FAILED : ReportExecutionStatus.SUCCESS,
                    queries: plans,
                    output: response.output ?: null,
                    exceptionStackTrace: response.exceptionStackTrace,
                    runningTime: response.runningTime)
        }
    }

    private static String resolveScript(ReportDefinition reportDefinition) {
        if (!reportDefinition.script?.trim()) {
            throw new ReportException("Report ${reportDefinition.name} has no script")
        }

        reportDefinition.script
    }

    private static DefaultReportScriptContext buildReportContext(String reportName, Map<String, Object> coercedValues,
                                                                 String script, String userId,
                                                                 ResourceResolver resourceResolver) {
        def outputStream = new ByteArrayOutputStream()

        new DefaultReportScriptContext(
                reportName: reportName,
                parameterValues: coercedValues,
                resourceResolver: resourceResolver,
                outputStream: outputStream,
                printStream: new PrintStream(outputStream, true, CHARSET),
                script: script,
                userId: userId
        )
    }
}
