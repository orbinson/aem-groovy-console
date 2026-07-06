package be.orbinson.aem.groovy.console.migrations;

import static be.orbinson.aem.groovy.console.migrations.FlagSectionPagesMigrationTest.TEMPLATE;
import static be.orbinson.aem.groovy.console.migrations.FlagSectionPagesMigrationTest.migration;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.stream.Collectors;

import javax.jcr.Node;
import javax.jcr.PropertyType;
import javax.jcr.Session;

import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import be.orbinson.aem.groovy.console.queryaudit.QueryPlanAuditor;
import be.orbinson.aem.groovy.console.queryaudit.spi.AuditedQuery;
import be.orbinson.aem.groovy.console.testing.ContextPlugins;
import be.orbinson.aem.groovy.console.testing.GroovyConsole;
import io.wcm.testing.mock.aem.junit5.AemContext;
import io.wcm.testing.mock.aem.junit5.AemContextBuilder;
import io.wcm.testing.mock.aem.junit5.AemContextExtension;

/**
 * Detects which Oak indexes the FlagSectionPages migration needs, by running it through the real query-audit service
 * ({@link QueryPlanAuditor}) against real Oak (JCR_OAK) and inspecting the plan Oak chose for each query. Demonstrates
 * both directions:
 * <ul>
 *   <li>with no custom index, the migration's query traverses -> the test lists it as needing an index;</li>
 *   <li>after installing a cq:template index, the same query is covered -> no query traverses.</li>
 * </ul>
 */
@ExtendWith(AemContextExtension.class)
class FlagSectionPagesIndexAuditTest {

    private final AemContext context = new AemContextBuilder(ResourceResolverType.JCR_OAK)
            .plugin(ContextPlugins.GROOVY_CONSOLE)
            .build();

    @Test
    void listsTheIndexesTheMigrationNeeds() {
        seedContent();

        List<AuditedQuery> queries = audit();

        List<AuditedQuery> needingIndex = queries.stream()
                .filter(AuditedQuery::isNeedsIndex)
                .collect(Collectors.toList());

        System.out.println("[audit] queries the migration runs: " + queries.size());
        needingIndex.forEach(q -> System.out.println(
                "[audit] NEEDS INDEX: " + q.getStatement() + "\n           plan: " + q.getPlan()));

        // The migration filters on cq:template, so without an index that query must traverse.
        assertTrue(needingIndex.stream().anyMatch(q -> q.getStatement().contains("cq:template")),
                "expected the cq:template query to require an index");
    }

    @Test
    void queriesAreCoveredOnceTheIndexDefinitionsAreInstalled() throws Exception {
        installPropertyIndex("cqTemplate", "cq:template");
        seedContent();

        List<AuditedQuery> queries = audit();

        queries.forEach(q -> System.out.println("[audit] " + (q.isNeedsIndex() ? "TRAVERSE" : "INDEXED ")
                + " | " + q.getPlan()));

        assertFalse(queries.isEmpty(), "expected at least one query to be audited");
        assertTrue(queries.stream().noneMatch(AuditedQuery::isNeedsIndex),
                "with the cq:template index installed no query should traverse");
    }

    /** Run the migration through the real query-audit service and return the queries it executed. */
    private List<AuditedQuery> audit() {
        Session session = context.resourceResolver().adaptTo(Session.class);
        return new QueryPlanAuditor()
                .audit(session, () -> GroovyConsole.runScript(context, migration()))
                .getQueries();
    }

    private void seedContent() {
        context.create().page("/content/we-retail", "otherTemplate", "We Retail");
        context.create().page("/content/we-retail/products", TEMPLATE, "Products");
    }

    /** Installs an Oak property index. propertyNames must be of type NAME for Oak to use it. */
    private void installPropertyIndex(String name, String property) throws Exception {
        Session session = context.resourceResolver().adaptTo(Session.class);
        Node index = session.getNode("/oak:index").addNode(name, "oak:QueryIndexDefinition");
        index.setProperty("type", "property");
        index.setProperty("propertyNames", new String[]{property}, PropertyType.NAME);
        index.setProperty("reindex", true);
        session.save();
    }
}
