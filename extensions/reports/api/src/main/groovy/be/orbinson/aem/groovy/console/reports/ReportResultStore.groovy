package be.orbinson.aem.groovy.console.reports

import be.orbinson.aem.groovy.console.reports.data.ReportData
import be.orbinson.aem.groovy.console.reports.model.ReportResultPage
import org.osgi.annotation.versioning.ProviderType

/**
 * Paged access to persisted report results.
 */
@ProviderType
interface ReportResultStore {

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
