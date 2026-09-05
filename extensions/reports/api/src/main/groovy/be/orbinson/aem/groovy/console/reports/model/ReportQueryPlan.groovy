package be.orbinson.aem.groovy.console.reports.model

import groovy.transform.ToString

/**
 * A single JCR query a report script executed, with the Oak query plan chosen for it on the live instance.
 * Deliberately independent of the (optional) query-audit extension's own types so reports-api never depends on it.
 */
@ToString(includePackage = false, includeNames = true)
class ReportQueryPlan {

    /** The executed query statement. */
    String statement

    /** The JCR query language of the statement, e.g. {@code JCR-SQL2} or {@code xpath}. */
    String language

    /** The Oak execution plan chosen for the statement. */
    String plan

    /** True when Oak found no covering index and fell back to a traversal. */
    boolean needsIndex
}
