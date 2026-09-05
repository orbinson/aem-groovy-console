package be.orbinson.aem.groovy.console.migration.impl

import be.orbinson.aem.groovy.console.queryaudit.spi.QueryAuditService
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

import javax.jcr.Session
import java.util.function.Supplier

/**
 * Bridges the migration extension to the optional query-audit extension. Its mandatory {@link QueryAuditService}
 * reference means Declarative Services only loads and registers this component when the query-audit bundle is
 * present — so {@link DefaultMigrationService}, which references only {@link ScriptIndexAuditor}, never touches a
 * query-audit type and keeps working when the extension is absent (e.g. AEM as a Cloud Service).
 */
@Component(service = ScriptIndexAuditor)
class QueryAuditScriptIndexAuditor implements ScriptIndexAuditor {

    @Reference
    private QueryAuditService queryAuditService

    @Override
    List<Map<String, Object>> audit(Session session, Runnable work) {
        queryAuditService.audit(session, { work.run(); null } as Supplier).queries.collect { it.toMap() }
    }
}
