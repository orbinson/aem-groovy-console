package be.orbinson.aem.groovy.console.reports

import org.osgi.annotation.versioning.ProviderType

/**
 * Registry of all available report exporters.  Export formats are API-driven: the set of available formats is
 * the set of registered {@link ReportExporter} services.
 */
@ProviderType
interface ReportExporterRegistry {

    /**
     * Get all registered exporters, sorted by format.
     *
     * @return list of exporters
     */
    List<ReportExporter> getExporters()

    /**
     * Get the exporter for the given format.
     *
     * @param format format identifier
     * @return exporter or null if no exporter is registered for the format
     */
    ReportExporter getExporter(String format)
}
