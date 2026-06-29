package be.orbinson.aem.groovy.console.reports

import be.orbinson.aem.groovy.console.reports.data.ReportData
import be.orbinson.aem.groovy.console.reports.model.ReportResultPage
import org.apache.sling.api.resource.Resource
import org.apache.sling.api.resource.ResourceResolver
import org.osgi.annotation.versioning.ProviderType

/**
 * Persists and reads report results.  A result is always stored as a single gzipped-JSON binary
 * ({@code jcr:data}) under the execution node, so it scales to any size without hitting JCR property limits.
 */
@ProviderType
interface ReportResultStore {

    /**
     * Persist a result as a gzipped-JSON binary child of the execution node, using the caller's resolver and
     * open transaction (the caller commits).
     *
     * @param resourceResolver resolver owning the open transaction
     * @param executionResource execution node to attach the result to
     * @param reportData result to persist
     */
    void save(ResourceResolver resourceResolver, Resource executionResource, ReportData reportData)

    /**
     * Get one page of a persisted result.
     *
     * @param executionId execution ID
     * @param page 1-based page number
     * @param pageSize page size; when 0 or negative the configured default applies
     * @return result page, or null when the execution or its result does not exist
     */
    ReportResultPage getPage(String executionId, int page, int pageSize)

    /**
     * Get the complete persisted result, e.g. for exports.
     *
     * @param executionId execution ID
     * @return report data, or null when the execution or its result does not exist
     */
    ReportData getData(String executionId)
}
