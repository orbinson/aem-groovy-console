package be.orbinson.aem.groovy.console.reports.model

import groovy.transform.ToString

/**
 * Cron schedule configured on a report definition.  When enabled, the report runs unattended on the cron
 * expression and its configured distributions are applied to the result.
 *
 * <p>A scheduled run has no request user, so it runs as {@link #runAs}.  Run-as is authorization-gated: it
 * defaults to the configurer ({@link #scheduledBy}) and may only name another user the configurer is permitted
 * to impersonate.  {@link #scheduledBy} is set server-side and the gate is re-checked at execution time, so a
 * schedule can never run with more rights than the person who configured it.
 */
@ToString(includePackage = false, includeNames = true)
class ReportSchedule {

    /** Whether the report runs on the schedule. */
    boolean enabled

    /** Quartz-style cron expression (as accepted by Sling's job scheduler). */
    String cronExpression

    /** User id the scheduled report runs as.  Defaults to {@link #scheduledBy}. */
    String runAs

    /** User id that configured the schedule; the run-as authorization is checked against this principal. */
    String scheduledBy

    /** Fixed raw (string) parameter values used for scheduled runs (there is no interactive form). */
    Map<String, String> parameterValues = [:]
}
