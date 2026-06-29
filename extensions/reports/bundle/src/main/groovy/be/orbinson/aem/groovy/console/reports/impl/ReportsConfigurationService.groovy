package be.orbinson.aem.groovy.console.reports.impl

/**
 * Internal access to the reports OSGi configuration.
 */
interface ReportsConfigurationService {

    int getDefaultPageSize()

    int getMaxOutputLength()
}
