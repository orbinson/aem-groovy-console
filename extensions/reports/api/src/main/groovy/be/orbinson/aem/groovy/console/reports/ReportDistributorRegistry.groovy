package be.orbinson.aem.groovy.console.reports

import org.osgi.annotation.versioning.ProviderType

/**
 * Registry of all available report distributors.  Distributors are API-driven: the set of available distributors
 * is the set of registered {@link ReportDistributor} services.
 */
@ProviderType
interface ReportDistributorRegistry {

    /**
     * Get all registered distributors, sorted by id.
     *
     * @return list of distributors
     */
    List<ReportDistributor> getDistributors()

    /**
     * Get the distributor for the given id.
     *
     * @param id distributor identifier
     * @return distributor or null if no distributor is registered for the id
     */
    ReportDistributor getDistributor(String id)
}
