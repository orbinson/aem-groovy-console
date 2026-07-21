package be.orbinson.aem.groovy.console.reports.model

import groovy.transform.ToString

/**
 * Report definition, persisted under <code>/conf/groovyconsole/reports</code>.
 */
@ToString(includePackage = false, includeNames = true, excludes = "script")
class ReportDefinition {

    /** Unique report name (node name, URL-safe). */
    String name

    /** Full repository path of the definition node; identifies the schedule and its trust origin (/conf vs /apps). */
    String path

    /** Display title. */
    String title

    /** Description shown in the reports UI. */
    String description

    /** Optional category for grouping reports in the UI. */
    String category

    /** Inline Groovy script executed to produce the report. */
    String script

    /** Result page size in the UI.  Falls back to the configured default when null. */
    Integer pageSize

    /** Declared parameters. */
    List<ReportParameter> parameters = []

    /** Optional cron schedule; null or disabled means the report only runs on demand. */
    ReportSchedule schedule

    /** Distribution targets applied to the result when the report finishes (scheduled or on demand). */
    List<ReportDistributionTarget> distributions = []

    Calendar created

    String createdBy

    Calendar lastModified

    String lastModifiedBy
}
