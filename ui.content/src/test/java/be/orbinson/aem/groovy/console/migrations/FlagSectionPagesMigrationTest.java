package be.orbinson.aem.groovy.console.migrations;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

import org.apache.commons.io.IOUtils;
import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import be.orbinson.aem.groovy.console.response.RunScriptResponse;
import be.orbinson.aem.groovy.console.testing.ContextPlugins;
import be.orbinson.aem.groovy.console.testing.GroovyConsole;
import io.wcm.testing.mock.aem.junit5.AemContext;
import io.wcm.testing.mock.aem.junit5.AemContextBuilder;
import io.wcm.testing.mock.aem.junit5.AemContextExtension;

/**
 * Functional test for the FlagSectionPages migration: it queries for section pages and flags them. Runs against real
 * Oak (JCR_OAK) because the migration relies on a JCR-SQL2 query, which the JCR mock does not execute.
 */
@ExtendWith(AemContextExtension.class)
class FlagSectionPagesMigrationTest {

    static final String TEMPLATE = "/conf/we-retail/settings/wcm/templates/section-page";

    private final AemContext context = new AemContextBuilder(ResourceResolverType.JCR_OAK)
            .plugin(ContextPlugins.GROOVY_CONSOLE)
            .build();

    @Test
    void flagsSectionPages() {
        context.create().page("/content/we-retail", "otherTemplate", "We Retail");
        context.create().page("/content/we-retail/products", TEMPLATE, "Products");
        context.create().page("/content/we-retail/about", "otherTemplate", "About");

        RunScriptResponse response = GroovyConsole.runScript(context, migration());

        assertTrue(response.getExceptionStackTrace().isEmpty(), response.getExceptionStackTrace());
        assertTrue(reviewed("/content/we-retail/products"), "section page should be flagged");
        assertFalse(reviewed("/content/we-retail/about"), "non-section page should not be flagged");
    }

    private boolean reviewed(String pagePath) {
        return context.resourceResolver().getResource(pagePath + "/jcr:content")
                .getValueMap().get("reviewed", false);
    }

    static String migration() {
        try {
            return IOUtils.toString(
                    FlagSectionPagesMigrationTest.class.getResourceAsStream("/migrations/FlagSectionPages.groovy"),
                    StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
