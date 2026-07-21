package be.orbinson.aem.groovy.console.reports

import be.orbinson.aem.groovy.console.reports.data.ReportData
import org.osgi.annotation.versioning.ConsumerType

/**
 * A {@link ReportExporter} whose output depends on the requesting user's locale (e.g. CSV, whose field
 * delimiter follows the locale's decimal separator).  The export servlet passes the request locale when the
 * exporter implements this; plain {@link ReportExporter}s are unaffected.
 */
@ConsumerType
interface LocaleAwareReportExporter extends ReportExporter {

    /**
     * Write the report data for a specific user locale.  Implementations must not close the stream.
     *
     * @param reportData report data to export
     * @param outputStream stream to write to
     * @param locale requesting user's locale
     */
    void export(ReportData reportData, OutputStream outputStream, Locale locale)
}
