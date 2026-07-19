package be.orbinson.aem.groovy.console.reports.constants

class ReportsConstants {

    public static final String SYSTEM_USER_NAME = "aem-groovy-console-reports-service"

    /** Dedicated identity that executes scheduled reports (reads content); separate from the bookkeeping user. */
    public static final String EXECUTOR_SYSTEM_USER_NAME = "aem-groovy-console-reports-executor"

    /** Sling subservice name mapped to the executor system user. */
    public static final String EXECUTOR_SUBSERVICE = "executor"

    public static final String PATH_REPORTS_FOLDER = "/conf/groovyconsole/reports"

    /**
     * Immutable location for report definitions deployed in code (content packages).  Definitions here are
     * auto-discovered, scheduled and run under the executor service user.  A dedicated sibling of
     * <code>/apps/groovyconsole-reports</code>, so installing either package leaves the other untouched.
     */
    public static final String PATH_APPS_REPORTS_FOLDER = "/apps/groovyconsole-reports-definitions"

    public static final String PATH_EXECUTIONS_FOLDER = "/var/groovyconsole/reports/executions"

    public static final String RESOURCE_TYPE_DEFINITION = "groovyconsole/reports/definition"

    public static final String CHARSET = "UTF-8"

    public static final String DATE_FORMAT_ISO_8601 = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"

    public static final String EXECUTION_NODE_PREFIX = "execution"

    public static final String PARAMETERS_NODE_NAME = "parameters"

    public static final String SCHEDULE_NODE_NAME = "schedule"

    public static final String DISTRIBUTIONS_NODE_NAME = "distributions"

    public static final String RESULT_NODE_NAME = "result"

    // Sling job topic for scheduled report runs

    public static final String JOB_TOPIC = "groovyconsole/reports/job"

    public static final String JOB_PROPERTY_REPORT_NAME = "reportName"

    /** Full node path of the scheduled report definition; the consumer loads the definition from here at run time. */
    public static final String JOB_PROPERTY_REPORT_PATH = "reportPath"

    // request parameters

    public static final String PARAMETER_NAME = "name"

    public static final String PARAMETER_EXECUTION_ID = "executionId"

    public static final String PARAMETER_PAGE = "page"

    public static final String PARAMETER_PAGE_SIZE = "pageSize"

    public static final String PARAMETER_FORMAT = "format"

    // JSON keys

    public static final String JSON_KEY_REPORT_DATA = "reportData"

    public static final String JSON_KEY_TABLE = "table"

    private ReportsConstants() {

    }
}
