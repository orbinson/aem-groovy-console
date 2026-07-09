package be.orbinson.aem.groovy.console.migration

class MigrationConstants {

    public static final String PATH_MIGRATION_ROOT = "/var/groovyconsole/migration"

    public static final String PATH_MIGRATION_REGISTRY = "$PATH_MIGRATION_ROOT/registry"

    public static final String PATH_MIGRATION_RUNS = "$PATH_MIGRATION_ROOT/runs"

    public static final String DEFAULT_SCRIPTS_BASE_PATH = "/conf/groovyconsole/scripts/migration"

    public static final String MIGRATION_JOB_TOPIC = "groovyconsole/migration"

    // request parameters

    public static final String RUN_ID = "runId"

    public static final String ASYNC = "async"

    public static final String DRY_RUN = "dryRun"

    public static final String REGISTRY = "registry"

    public static final String PENDING = "pending"

    /** Optional path to scope a run to, instead of the configured scripts base path. */
    public static final String PATH = "path"

    /** Optional JSON or String data to be made available to scripts as the "data" binding variable. */
    public static final String DATA = "data"

    // triggers

    public static final String TRIGGER_API = "API"

    public static final String TRIGGER_LISTENER = "LISTENER"

    public static final String TRIGGER_JMX = "JMX"

    // resource node names

    public static final String NN_RESULTS = "results"

    // resource property names

    public static final String PN_SCRIPT_PATH = "scriptPath"

    public static final String PN_CHECKSUM = "checksum"

    public static final String PN_STATUS = "status"

    public static final String PN_LAST_RUN_DATE = "lastRunDate"

    public static final String PN_RUNNING_TIME = "runningTime"

    public static final String PN_TRIGGER = "trigger"

    public static final String PN_START_DATE = "startDate"

    public static final String PN_END_DATE = "endDate"

    public static final String PN_DURATION_MILLIS = "durationMillis"

    public static final String PN_OUTPUT = "output"

    public static final String PN_ERROR = "error"

    public static final String PN_RUNNING = "running"

    public static final String PN_RUN_STARTED_AT = "runStartedAt"

    private MigrationConstants() {

    }
}
