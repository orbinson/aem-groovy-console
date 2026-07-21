package be.orbinson.aem.groovy.console.reports.model

import be.orbinson.aem.groovy.console.reports.data.ReportColumn
import groovy.transform.ToString

/**
 * One page of a persisted report result.  Pages are 1-based.
 */
@ToString(includePackage = false, includeNames = true, excludes = "rows")
class ReportResultPage {

    List<ReportColumn> columns = []

    List<List<Object>> rows = []

    /** Current page, 1-based. */
    int page

    int pageSize

    long totalRows

    int totalPages

    /**
     * @return the next page number, or -1 when this is the last page
     */
    int getNextPage() {
        page < totalPages ? page + 1 : -1
    }

    /**
     * @return the previous page number, or -1 when this is the first page
     */
    int getPreviousPage() {
        page > 1 ? page - 1 : -1
    }
}
