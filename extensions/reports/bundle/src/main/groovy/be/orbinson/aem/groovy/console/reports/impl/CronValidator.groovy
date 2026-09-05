package be.orbinson.aem.groovy.console.reports.impl

/**
 * Validates cron expressions for scheduled reports before they are persisted or registered with the Sling
 * scheduler, so an invalid expression is rejected with a clear message instead of failing (or silently not
 * scheduling) later.
 *
 * <p>Sling's scheduler uses the Quartz form: 6 or 7 whitespace-separated fields
 * (<code>seconds minutes hours day-of-month month day-of-week [year]</code>).  Beyond a structural check this
 * also rejects sub-minute schedules, which are almost always a mistake for a reporting job and an easy way to
 * hammer the instance.
 */
class CronValidator {

    private CronValidator() {
    }

    static void validate(String cronExpression) {
        def expression = cronExpression?.trim()

        if (!expression) {
            throw new IllegalArgumentException("A cron expression is required for an enabled schedule.")
        }

        def fields = expression.split(/\s+/)

        if (fields.length < 6 || fields.length > 7) {
            throw new IllegalArgumentException("Invalid cron expression '${expression}': expected 6 or 7 fields " +
                    "(seconds minutes hours day-of-month month day-of-week [year]).")
        }

        // the seconds field must be a fixed value so a report cannot be scheduled to run every second / few seconds
        def seconds = fields[0]

        if (!(seconds ==~ /\d{1,2}/) || (seconds as int) > 59) {
            throw new IllegalArgumentException("Invalid cron expression '${expression}': the seconds field must be " +
                    "a fixed value between 0 and 59; sub-minute schedules are not allowed.")
        }
    }
}
