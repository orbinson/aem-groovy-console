package be.orbinson.aem.groovy.console.reports.model

import groovy.transform.ToString

/**
 * A single distribution target configured on a report definition: which {@code ReportDistributor} to use, which
 * export format to render, and distributor-specific configuration (recipients, directory, ...).  Persisted as a
 * child node under the report definition's <code>distributions</code> node and applied when the report finishes.
 */
@ToString(includePackage = false, includeNames = true)
class ReportDistributionTarget {

    /** Id of the {@code ReportDistributor} to use, e.g. "email" or "filesystem". */
    String distributorId

    /** Export format id ({@code ReportExporter.format}) used to render the payload, e.g. "csv" or "xlsx". */
    String format

    /** Distributor-specific configuration (e.g. recipients and subject, or directory and filename). */
    Map<String, Object> config = [:]
}
