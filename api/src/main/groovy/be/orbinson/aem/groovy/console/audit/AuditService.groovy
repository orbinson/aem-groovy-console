package be.orbinson.aem.groovy.console.audit

import be.orbinson.aem.groovy.console.response.RunScriptResponse
import org.apache.sling.api.SlingHttpServletRequest
import org.osgi.annotation.versioning.ProviderType

@ProviderType
interface AuditService {

    /**
     * Create an audit record for the given script execution response.
     *
     * @param response response containing execution result or exception
     */
    AuditRecord createAuditRecord(RunScriptResponse response)

    /**
     * Delete all audit records.
     *
     * @param userId user that owns the audit records
     */
    void deleteAllAuditRecords(String userId)

    /**
     * Delete an audit record.
     *
     * @param userId user that owns the audit record
     * @param relativePath relative path to audit record from parent audit resource
     */
    void deleteAuditRecord(String userId, String relativePath)

    /**
     * Get all audit records.
     *
     * @param userId user that owns the audit records
     * @return all audit records
     */
    List<AuditRecord> getAllAuditRecords(String userId)

    /**
     * Get all audit records for scheduled jobs.
     *
     * @return all audit records for scheduled jobs
     */
    List<AuditRecord> getAllScheduledJobAuditRecords()

    /**
     * Get the audit record for the given job ID.
     *
     * @param jobId Sling-generated ID for the job
     * @return audit record or null if not found
     */
    AuditRecord getAuditRecord(String jobId)

    /**
     * Get the audit record at the given relative path.
     *
     * @param userId user that owns the audit record
     * @param relativePath relative path to audit record from parent audit node
     * @return audit record or null if none exists
     */
    AuditRecord getAuditRecord(String userId, String relativePath)

    /**
     * Get an audit record for a request, enforcing access control: the record is returned only when the requesting
     * user is allowed to see it (their own record, a scheduled-job record when they hold the scheduled-job
     * permission, or any record when "display all audit records" is enabled — and always requiring the console
     * permission). Returns {@code null} when the record does not exist or the user may not access it, so callers
     * cannot read another user's records by supplying a different user ID.
     *
     * @param request current request
     * @param userId user that owns the audit record
     * @param relativePath relative path to audit record from parent audit node
     * @return audit record, or null if it does not exist or is not accessible to the requesting user
     */
    AuditRecord getAuditRecord(SlingHttpServletRequest request, String userId, String relativePath)

    /**
     * Delete an audit record for a request, enforcing the same access control as
     * {@link #getAuditRecord(SlingHttpServletRequest, String, String)}. No-op when the user may not access the record.
     *
     * @param request current request
     * @param userId user that owns the audit record
     * @param relativePath relative path to audit record from parent audit node
     * @return true if the record was accessible and deletion was attempted, false if access was denied
     */
    boolean deleteAuditRecord(SlingHttpServletRequest request, String userId, String relativePath)

    /**
     * Get a list of audit records for the given date range.
     *
     * @param userId user that owns the audit records
     * @param startDate start date
     * @param endDate end date
     * @return list of audit records in the given date range
     */
    List<AuditRecord> getAuditRecords(String userId, Calendar startDate, Calendar endDate)

    /**
     * Get a list of scheduled job audit records for the given date range.
     *
     * @param startDate start date
     * @param endDate end date
     * @return list of scheduled job audit records in the given date range
     */
    List<AuditRecord> getScheduledJobAuditRecords(Calendar startDate, Calendar endDate)
}
