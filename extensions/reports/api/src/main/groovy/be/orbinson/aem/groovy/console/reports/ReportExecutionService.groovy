package be.orbinson.aem.groovy.console.reports

import be.orbinson.aem.groovy.console.reports.model.ReportDefinition
import be.orbinson.aem.groovy.console.reports.model.ReportExecution
import be.orbinson.aem.groovy.console.reports.model.ReportPreview
import be.orbinson.aem.groovy.console.reports.model.ReportQueryAudit
import org.apache.sling.api.resource.ResourceResolver
import org.osgi.annotation.versioning.ProviderType

/**
 * Executes reports and manages persisted executions.  A report runs once per execution, <em>asynchronously</em>
 * on a background thread, and the full tabular result is persisted under
 * <code>/var/groovyconsole/reports/executions</code>.  Pagination and exports read the persisted result.
 */
@ProviderType
interface ReportExecutionService {

    /**
     * Start a report execution asynchronously and return immediately.  The script runs on a background thread;
     * the returned execution is initially {@code RUNNING} and transitions to {@code SUCCESS}/{@code FAILED} once
     * it finishes — callers poll {@link #getExecution(String)} for the outcome.
     *
     * @param reportDefinition report to execute
     * @param parameterValues raw (string) parameter values, validated and coerced against the declared parameters
     * @param resourceResolver resolver of the requesting user; the script executes with a detached clone of it,
     *        so the user's JCR ACLs apply
     * @return the started execution (status RUNNING, or already terminal for a very fast run)
     * @throws IllegalArgumentException when parameter validation fails
     */
    ReportExecution execute(ReportDefinition reportDefinition, Map<String, String> parameterValues,
                            ResourceResolver resourceResolver)

    /**
     * Run a report script once for a "try out" preview WITHOUT persisting an execution.  Used by the report
     * editor to debug an unsaved script: it runs through the same {@code ReportScriptContext} (so the
     * <code>report</code> and <code>params</code> bindings and typed result behave exactly as in a real run)
     * and returns the typed result, captured output and any failure directly.
     *
     * @param reportDefinition the (possibly unsaved) report to run
     * @param parameterValues raw (string) test values, validated and coerced against the declared parameters
     * @param resourceResolver resolver of the requesting user
     * @return the preview result (status SUCCESS or FAILED)
     * @throws IllegalArgumentException when parameter validation fails
     */
    ReportPreview preview(ReportDefinition reportDefinition, Map<String, String> parameterValues,
                          ResourceResolver resourceResolver)

    /**
     * Whether query auditing is available — i.e. the optional query-audit extension is installed. Query capture
     * relies on logback manipulation, so the extension is intended for local / on-prem (non-Cloud) use; where it is
     * absent {@link #auditQueries} cannot run and callers should not offer it.
     *
     * @return true when {@link #auditQueries} can be used
     */
    boolean isQueryAuditAvailable()

    /**
     * Run a report script once — like {@link #preview} (detached resolver, nothing persisted) — but instead of the
     * typed result, report per JCR query the script executed whether the live Oak repository has a covering index.
     * Lets report editors verify that a report (an often-run query) is index-backed.
     *
     * @param reportDefinition the (possibly unsaved) report to run
     * @param parameterValues raw (string) test values, validated and coerced against the declared parameters
     * @param resourceResolver resolver of the requesting user
     * @return the audit result (status SUCCESS or FAILED, plus the per-query plans); an empty audit when query
     *         auditing is not available (see {@link #isQueryAuditAvailable})
     * @throws IllegalArgumentException when parameter validation fails
     */
    ReportQueryAudit auditQueries(ReportDefinition reportDefinition, Map<String, String> parameterValues,
                                  ResourceResolver resourceResolver)

    /**
     * Get all executions of a report, newest first.
     *
     * @param reportName report name
     * @return executions
     */
    List<ReportExecution> getExecutions(String reportName)

    /**
     * Get an execution by ID.
     *
     * @param executionId execution ID
     * @return execution or null if not found
     */
    ReportExecution getExecution(String executionId)

    /**
     * Delete an execution and its persisted result.
     *
     * @param executionId execution ID
     */
    void deleteExecution(String executionId)
}
