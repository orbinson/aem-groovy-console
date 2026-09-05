package be.orbinson.aem.groovy.console.reports.impl

import be.orbinson.aem.groovy.console.queryaudit.spi.QueryAuditService
import be.orbinson.aem.groovy.console.reports.model.ReportQueryPlan
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

import javax.jcr.Session
import java.util.function.Supplier

/**
 * Bridges the reports extension to the optional query-audit extension. Its mandatory {@link QueryAuditService}
 * reference means Declarative Services only loads and registers this component when the query-audit bundle is
 * present — so {@link DefaultReportExecutionService}, which references only {@link ReportScriptIndexAuditor}, never
 * touches a query-audit type and keeps working when the extension is absent (e.g. AEM as a Cloud Service).
 */
@Component(service = ReportScriptIndexAuditor)
class QueryAuditReportIndexAuditor implements ReportScriptIndexAuditor {

    @Reference
    private QueryAuditService queryAuditService

    @Override
    List<ReportQueryPlan> audit(Session session, Runnable work) {
        queryAuditService.audit(session, { work.run(); null } as Supplier).queries.collect { query ->
            new ReportQueryPlan(statement: query.statement, language: query.language, plan: query.plan,
                    needsIndex: query.needsIndex)
        }
    }
}
