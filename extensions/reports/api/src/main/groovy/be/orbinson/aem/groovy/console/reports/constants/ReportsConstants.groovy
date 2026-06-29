package be.orbinson.aem.groovy.console.reports.constants

class ReportsConstants {

    public static final String SYSTEM_USER_NAME = "aem-groovy-console-reports-service"

    public static final String PATH_REPORTS_FOLDER = "/conf/groovyconsole/reports"

    public static final String PATH_EXECUTIONS_FOLDER = "/var/groovyconsole/reports/executions"

    public static final String RESOURCE_TYPE_DEFINITION = "groovyconsole/reports/definition"

    public static final String CHARSET = "UTF-8"

    public static final String DATE_FORMAT_ISO_8601 = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"

    public static final String EXECUTION_NODE_PREFIX = "execution"

    public static final String PARAMETERS_NODE_NAME = "parameters"

    public static final String RESULT_NODE_NAME = "result"

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
