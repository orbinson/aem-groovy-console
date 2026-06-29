package be.orbinson.aem.groovy.console.reports.impl

import be.orbinson.aem.groovy.console.reports.ReportExporter
import be.orbinson.aem.groovy.console.reports.ReportExporterRegistry
import groovy.transform.Synchronized
import groovy.util.logging.Slf4j
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ReferenceCardinality
import org.osgi.service.component.annotations.ReferencePolicy

import java.util.concurrent.CopyOnWriteArrayList

@Component(service = ReportExporterRegistry, immediate = true)
@Slf4j("LOG")
class DefaultReportExporterRegistry implements ReportExporterRegistry {

    private volatile List<ReportExporter> exporters = new CopyOnWriteArrayList<>()

    @Override
    List<ReportExporter> getExporters() {
        exporters.sort(false) { exporter -> exporter.format }
    }

    @Override
    ReportExporter getExporter(String format) {
        exporters.find { exporter -> exporter.format == format }
    }

    @Reference(cardinality = ReferenceCardinality.MULTIPLE, policy = ReferencePolicy.DYNAMIC)
    @Synchronized
    void bindReportExporter(ReportExporter exporter) {
        exporters.add(exporter)

        LOG.info("added report exporter for format : {}", exporter.format)
    }

    @Synchronized
    void unbindReportExporter(ReportExporter exporter) {
        exporters.remove(exporter)

        LOG.info("removed report exporter for format : {}", exporter.format)
    }
}
