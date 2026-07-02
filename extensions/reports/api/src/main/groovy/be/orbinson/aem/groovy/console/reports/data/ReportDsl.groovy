package be.orbinson.aem.groovy.console.reports.data

import be.orbinson.aem.groovy.console.table.Table

/**
 * Helper exposed to report scripts as the <code>report</code> binding.
 */
class ReportDsl {

    /**
     * Create a new typed report data builder.
     *
     * @return empty report data
     */
    ReportData data() {
        new ReportData()
    }

    /**
     * Create a new (untyped) console table.  Supported for compatibility with existing console scripts; all
     * columns are treated as strings.
     *
     * @return empty table
     */
    Table table() {
        new Table()
    }
}
