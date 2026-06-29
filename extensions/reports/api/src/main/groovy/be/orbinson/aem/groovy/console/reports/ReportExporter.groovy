package be.orbinson.aem.groovy.console.reports

import be.orbinson.aem.groovy.console.reports.data.ReportData
import org.osgi.annotation.versioning.ConsumerType

/**
 * Services may implement this interface to provide an additional export format for report results.  Available
 * formats are discovered dynamically; registering a new exporter automatically surfaces it in the reports API
 * and UI.
 */
@ConsumerType
interface ReportExporter {

    /**
     * Format identifier, e.g. <code>csv</code>.  Used as the <code>format</code> request parameter on the
     * export endpoint.
     *
     * @return format identifier
     */
    String getFormat()

    /**
     * Content type of the exported file, e.g. <code>text/csv</code>.
     *
     * @return content type
     */
    String getContentType()

    /**
     * File extension of the exported file (without leading dot), e.g. <code>csv</code>.
     *
     * @return file extension
     */
    String getFileExtension()

    /**
     * Write the given report data to the output stream.  Implementations must not close the stream.
     *
     * @param reportData report data to export
     * @param outputStream stream to write to
     */
    void export(ReportData reportData, OutputStream outputStream)
}
