package be.orbinson.aem.groovy.console.reports

import be.orbinson.aem.groovy.console.reports.model.ReportDefinition
import be.orbinson.aem.groovy.console.reports.model.ReportDistributionTarget
import be.orbinson.aem.groovy.console.reports.model.ReportExecution
import be.orbinson.aem.groovy.console.reports.model.ReportPreview
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
     * Start a report execution and apply the given distribution targets to the result once it finishes
     * successfully.  Behaves exactly like {@link #execute(ReportDefinition, Map, ResourceResolver)} otherwise;
     * distribution failures are recorded against the execution but do not fail the run.
     *
     * @param reportDefinition report to execute
     * @param parameterValues raw (string) parameter values
     * @param resourceResolver resolver the script executes under (a detached clone is used)
     * @param distributionTargets distribution targets to apply on success (may be empty)
     * @return the started execution
     */
    ReportExecution execute(ReportDefinition reportDefinition, Map<String, String> parameterValues,
                            ResourceResolver resourceResolver, List<ReportDistributionTarget> distributionTargets)

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
     * Apply the given distribution targets to an already-completed successful execution, using its persisted
     * result.  Distribution failures are recorded against the execution.  Used for on-demand distribution of a
     * run that has already finished.
     *
     * @param executionId execution ID (must be a successful execution)
     * @param distributionTargets distribution targets to apply
     * @throws be.orbinson.aem.groovy.console.reports.ReportException when the execution is missing, not
     *         successful, or has no stored result
     */
    void distribute(String executionId, List<ReportDistributionTarget> distributionTargets)

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
