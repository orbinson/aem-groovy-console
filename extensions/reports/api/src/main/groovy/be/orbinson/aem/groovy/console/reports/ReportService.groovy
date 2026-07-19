package be.orbinson.aem.groovy.console.reports

import be.orbinson.aem.groovy.console.reports.model.ReportDefinition
import org.apache.sling.api.resource.ResourceResolver
import org.osgi.annotation.versioning.ProviderType

/**
 * CRUD service for report definitions stored under <code>/conf/groovyconsole/reports</code>.  All repository
 * access uses the caller-supplied resolver, so JCR access control governs authorization: read access to a
 * report node permits viewing and running it; write access permits creating, editing and deleting it.
 */
@ProviderType
interface ReportService {

    /**
     * Get the report definitions readable with the given resolver.
     *
     * @param resourceResolver requesting user's resolver
     * @return report definitions sorted by title
     */
    List<ReportDefinition> getReports(ResourceResolver resourceResolver)

    /**
     * Get a report definition by name, or null if it does not exist or is not readable.
     *
     * @param resourceResolver requesting user's resolver
     * @param name report name
     * @return report definition or null
     */
    ReportDefinition getReport(ResourceResolver resourceResolver, String name)

    /**
     * Get the report definition at an exact repository path, or null if there is none there or it is not readable.
     * Unlike {@link #getReport}, this resolves definitions anywhere (e.g. the immutable <code>/apps</code>
     * drop-zone), which is how scheduled jobs load the definition they were registered for.
     *
     * @param resourceResolver requesting resolver
     * @param path full definition node path
     * @return report definition or null
     */
    ReportDefinition getReportAtPath(ResourceResolver resourceResolver, String path)

    /**
     * Recursively collect the report definitions under a base path (used to discover code-deployed definitions).
     *
     * @param resourceResolver requesting resolver
     * @param basePath folder to scan
     * @return report definitions found beneath the base path (empty when the path is missing or unreadable)
     */
    List<ReportDefinition> findReports(ResourceResolver resourceResolver, String basePath)

    /**
     * Create or update a report definition.  The write uses the given resolver, so it fails when the user
     * lacks JCR write access to the reports tree.
     *
     * @param resourceResolver requesting user's resolver
     * @param reportDefinition definition to persist
     * @param userId user performing the change (recorded as created/last modified by)
     * @return persisted definition
     */
    ReportDefinition saveReport(ResourceResolver resourceResolver, ReportDefinition reportDefinition, String userId)

    /**
     * Delete a report definition with the given resolver.
     *
     * @param resourceResolver requesting user's resolver
     * @param name report name
     */
    void deleteReport(ResourceResolver resourceResolver, String name)

    /**
     * Whether the user may create reports, i.e. has JCR write access to the reports folder.
     *
     * @param resourceResolver requesting user's resolver
     * @return true if the user can create reports
     */
    boolean canCreate(ResourceResolver resourceResolver)

    /**
     * Whether the user may edit/delete the named report, i.e. has JCR write access to its node.
     *
     * @param resourceResolver requesting user's resolver
     * @param name report name
     * @return true if the user can edit the report
     */
    boolean canEdit(ResourceResolver resourceResolver, String name)
}
