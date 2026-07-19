package be.orbinson.aem.groovy.console.reports.distributor

import be.orbinson.aem.groovy.console.reports.ReportException
import be.orbinson.aem.groovy.console.reports.ReportExporterRegistry
import be.orbinson.aem.groovy.console.reports.data.ReportColumnType
import be.orbinson.aem.groovy.console.reports.data.ReportData
import be.orbinson.aem.groovy.console.reports.exporter.CsvReportExporter
import be.orbinson.aem.groovy.console.reports.impl.FilesystemReportDistributorConfig
import be.orbinson.aem.groovy.console.reports.model.ReportDistributionTarget
import be.orbinson.aem.groovy.console.reports.model.ReportExecution
import be.orbinson.aem.groovy.console.reports.model.ReportExecutionStatus
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

import java.nio.file.Path

import static org.junit.jupiter.api.Assertions.assertEquals
import static org.junit.jupiter.api.Assertions.assertThrows
import static org.junit.jupiter.api.Assertions.assertTrue

class FilesystemReportDistributorTest {

    private static final ReportExporterRegistry EXPORTER_REGISTRY = [
            getExporter : { String format -> format == "csv" ? new CsvReportExporter() : null },
            getExporters: { [new CsvReportExporter()] }
    ] as ReportExporterRegistry

    private static ReportData reportData() {
        def data = new ReportData()
        data.column("Name", ReportColumnType.STRING)
        data.column("Views", ReportColumnType.NUMBER)
        data.row("Home", 42)
        data
    }

    private static ReportExecution execution() {
        new ReportExecution(reportName: "traffic", status: ReportExecutionStatus.SUCCESS, finishedAt: Calendar.instance)
    }

    private static FilesystemReportDistributor distributor(String root, boolean enabled = true) {
        def distributor = new FilesystemReportDistributor()
        distributor.exporterRegistry = EXPORTER_REGISTRY
        distributor.activate([enabled: { enabled }, allowedRootDirectory: { root }] as FilesystemReportDistributorConfig)
        distributor
    }

    private static ReportDistributionTarget target(Map config) {
        new ReportDistributionTarget(distributorId: "filesystem", format: "csv", config: config)
    }

    @Test
    void "writes the rendered export into the configured directory"(@TempDir Path tempDir) {
        def distributor = distributor(tempDir.toString())

        distributor.distribute(execution(), reportData(), target([directory: "out", filename: "report.csv"]))

        def file = tempDir.resolve("out/report.csv").toFile()
        assertTrue(file.exists(), "expected the export file to be written")
        assertTrue(file.text.contains("Home"), "expected the export to contain the report data")
    }

    @Test
    void "defaults the filename when none is configured"(@TempDir Path tempDir) {
        def distributor = distributor(tempDir.toString())

        distributor.distribute(execution(), reportData(), target([directory: "."]))

        def written = tempDir.toFile().listFiles({ file -> file.name.endsWith(".csv") } as FileFilter)
        assertEquals(1, written.length)
        assertTrue(written[0].name.startsWith("traffic-"), "expected report-timestamp default filename")
    }

    @Test
    void "rejects a directory that escapes the allowed root"(@TempDir Path tempDir) {
        def distributor = distributor(tempDir.resolve("sandbox").toString())

        assertThrows(ReportException) {
            distributor.distribute(execution(), reportData(), target([directory: "../escape"]))
        }
    }

    @Test
    void "rejects a filename that escapes the target directory"(@TempDir Path tempDir) {
        def distributor = distributor(tempDir.toString())

        assertThrows(ReportException) {
            distributor.distribute(execution(), reportData(), target([directory: "out", filename: "../../etc/passwd"]))
        }
    }

    @Test
    void "fails when disabled"(@TempDir Path tempDir) {
        def distributor = distributor(tempDir.toString(), false)

        assertThrows(ReportException) {
            distributor.distribute(execution(), reportData(), target([directory: "out"]))
        }
    }

    @Test
    void "fails when the target has no directory"(@TempDir Path tempDir) {
        def distributor = distributor(tempDir.toString())

        assertThrows(ReportException) {
            distributor.distribute(execution(), reportData(), target([:]))
        }
    }
}
