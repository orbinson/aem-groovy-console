package be.orbinson.aem.groovy.console.reports

import be.orbinson.aem.groovy.console.reports.model.ReportDefinition
import be.orbinson.aem.groovy.console.reports.model.ReportQueryAudit
import org.apache.sling.api.resource.ResourceResolver
import org.osgi.annotation.versioning.ProviderType

/**
 * Runs a report script and reports, per JCR query it executes, whether the live Oak repository has a covering index —
 * so report authors can check that a report (an often-run query) is index-backed before shipping it.
 * <p>
 * A separate service from {@link ReportExecutionService} so query auditing stays an isolated, optional concern: it is
 * backed by the optional query-audit extension and is a no-op when that extension is not installed (see
 * {@link #isAvailable}). Callers that don't need auditing never touch it.
 */
@ProviderType
interface ReportQueryAuditService {

    /**
     * Whether query auditing is available — i.e. the optional query-audit extension is installed. Query capture relies
     * on logback manipulation, so the extension is intended for local / on-prem (non-Cloud) use; where it is absent
     * {@link #audit} cannot run and callers should not offer it.
     *
     * @return true when {@link #audit} can be used
     */
    boolean isAvailable()

    /**
     * Run a report script once — like {@code ReportExecutionService.preview} (detached resolver, nothing persisted) —
     * but instead of the typed result, report per JCR query the script executed whether the live Oak repository has a
     * covering index.
     *
     * @param reportDefinition the (possibly unsaved) report to run
     * @param parameterValues raw (string) test values, validated and coerced against the declared parameters
     * @param resourceResolver resolver of the requesting user
     * @return the audit result (status SUCCESS or FAILED, plus the per-query plans); an empty audit when query
     *         auditing is not available (see {@link #isAvailable})
     * @throws IllegalArgumentException when parameter validation fails
     */
    ReportQueryAudit audit(ReportDefinition reportDefinition, Map<String, String> parameterValues,
                           ResourceResolver resourceResolver)
}
