package be.orbinson.aem.groovy.console.reports

import be.orbinson.aem.groovy.console.reports.model.ReportDefinition
import be.orbinson.aem.groovy.console.reports.model.ReportExecution
import be.orbinson.aem.groovy.console.reports.model.ReportPreview
import org.apache.sling.api.resource.ResourceResolver
import org.osgi.annotation.versioning.ProviderType

/**
 * Executes reports and manages persisted executions.  A report is executed once per execution: the script runs
 * synchronously through the Groovy Console and the full tabular result is persisted under
 * <code>/var/groovyconsole/reports/executions</code>.  Pagination and exports read the persisted result.
 */
@ProviderType
interface ReportExecutionService {

    /**
     * Execute a report synchronously and persist the result.
     *
     * @param reportDefinition report to execute
     * @param parameterValues raw (string) parameter values, validated and coerced against the declared parameters
     * @param resourceResolver resolver of the requesting user; the script executes with this resolver so JCR
     *        ACLs of the user apply
     * @return the finished execution (status SUCCESS or FAILED)
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
