package be.orbinson.aem.groovy.console.reports.impl

/**
 * Internal access to the reports OSGi configuration.
 */
interface ReportsConfigurationService {

    int getDefaultPageSize()

    /** Maximum result rows persisted per execution; 0 means unlimited. */
    int getMaxResultRows()

    /** Whether reports may run on a schedule. */
    boolean isSchedulingEnabled()

    /** Whether report results may be distributed (scheduled or manual). */
    boolean isDistributionEnabled()
}
