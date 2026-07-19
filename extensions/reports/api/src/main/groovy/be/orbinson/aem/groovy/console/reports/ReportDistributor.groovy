package be.orbinson.aem.groovy.console.reports

import be.orbinson.aem.groovy.console.reports.data.ReportData
import be.orbinson.aem.groovy.console.reports.model.ReportDistributionTarget
import be.orbinson.aem.groovy.console.reports.model.ReportExecution
import org.osgi.annotation.versioning.ConsumerType

/**
 * Services may implement this interface to distribute a completed report execution somewhere (e.g. by email or to
 * the filesystem).  Available distributors are discovered dynamically; registering a new distributor automatically
 * surfaces it in the reports API and UI.  Distributors render the tabular result through a {@link ReportExporter}
 * (chosen via {@link ReportDistributionTarget#getFormat()}), so any registered export format can be distributed.
 */
@ConsumerType
interface ReportDistributor {

    /**
     * Distributor identifier, e.g. <code>email</code>.  Referenced by {@link ReportDistributionTarget#getDistributorId()}.
     *
     * @return distributor identifier
     */
    String getId()

    /**
     * Human-readable name for display in the UI.
     *
     * @return display name
     */
    String getName()

    /**
     * Distribute the result of a completed execution.  Implementations should throw {@link ReportException} on
     * failure so the caller can record it against the execution.
     *
     * @param execution completed execution metadata
     * @param reportData tabular result to distribute
     * @param target distribution target holding the export format and distributor-specific configuration
     */
    void distribute(ReportExecution execution, ReportData reportData, ReportDistributionTarget target)
}
