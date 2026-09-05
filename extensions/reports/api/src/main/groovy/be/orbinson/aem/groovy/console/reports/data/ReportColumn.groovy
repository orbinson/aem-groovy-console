package be.orbinson.aem.groovy.console.reports.data

import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString

/**
 * Typed report column.
 */
@ToString(includePackage = false, includeNames = true)
@EqualsAndHashCode
class ReportColumn {

    /** Column display name. */
    String name

    /** Column type. */
    ReportColumnType type = ReportColumnType.STRING

    /** Whether this column is included in CSV/XLSX exports. UI-only columns (e.g. an edit link) set this false. */
    boolean exported = true
}
