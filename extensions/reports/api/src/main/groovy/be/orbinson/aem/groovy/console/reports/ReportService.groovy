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
     * Update only the metadata of an existing report — title, description, category and page size — leaving the
     * executable Groovy (report script and any dynamic-options scripts) and the parameter definitions untouched.
     * Because it writes only the report node's own properties, it succeeds for a user who has
     * {@code jcr:modifyProperties} on the report node but is denied write on the {@code .groovy} script nodes.
     * Used to let non–console-permitted users edit report metadata without being able to change runnable code.
     *
     * @param resourceResolver requesting user's resolver
     * @param reportDefinition definition carrying the new metadata (only name + metadata fields are read)
     * @param userId user performing the change (recorded as last modified by)
     * @return the updated definition
     */
    ReportDefinition updateReportMetadata(ResourceResolver resourceResolver, ReportDefinition reportDefinition,
                                          String userId)

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
