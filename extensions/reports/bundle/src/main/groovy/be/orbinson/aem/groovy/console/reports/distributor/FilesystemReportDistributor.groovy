package be.orbinson.aem.groovy.console.reports.distributor

import be.orbinson.aem.groovy.console.reports.ReportDistributor
import be.orbinson.aem.groovy.console.reports.ReportException
import be.orbinson.aem.groovy.console.reports.ReportExporterRegistry
import be.orbinson.aem.groovy.console.reports.data.ReportData
import be.orbinson.aem.groovy.console.reports.impl.FilesystemReportDistributorConfig
import be.orbinson.aem.groovy.console.reports.model.ReportDistributionTarget
import be.orbinson.aem.groovy.console.reports.model.ReportExecution
import groovy.util.logging.Slf4j
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Modified
import org.osgi.service.component.annotations.Reference
import org.osgi.service.metatype.annotations.Designate

import java.nio.file.Files

/**
 * Distributes report results to the local filesystem.  Disabled by default; when enabled, every target directory
 * must resolve within the configured allowed root directory (canonical-path check), which prevents a report editor
 * from writing outside the sandbox via {@code ..} segments or an absolute path.
 */
@Component(service = ReportDistributor, immediate = true)
@Designate(ocd = FilesystemReportDistributorConfig)
@Slf4j("LOG")
class FilesystemReportDistributor implements ReportDistributor {

    static final String ID = "filesystem"

    /** Target config key: directory to write to, resolved within the allowed root. */
    static final String CONFIG_DIRECTORY = "directory"

    /** Target config key: optional explicit file name; defaults to {@code <report>-<timestamp>.<ext>}. */
    static final String CONFIG_FILENAME = "filename"

    @Reference
    private ReportExporterRegistry exporterRegistry

    private volatile boolean enabled

    private volatile String allowedRootDirectory

    @Activate
    @Modified
    void activate(FilesystemReportDistributorConfig config) {
        enabled = config.enabled()
        allowedRootDirectory = config.allowedRootDirectory()?.trim()
    }

    @Override
    String getId() {
        ID
    }

    @Override
    String getName() {
        "Filesystem"
    }

    @Override
    void distribute(ReportExecution execution, ReportData reportData, ReportDistributionTarget target) {
        if (!enabled) {
            throw new ReportException("filesystem distributor is disabled")
        }

        if (!allowedRootDirectory) {
            throw new ReportException("filesystem distributor has no allowed root directory configured")
        }

        def directory = (target.config[CONFIG_DIRECTORY] as String)?.trim()

        if (!directory) {
            throw new ReportException("filesystem distribution target has no directory configured")
        }

        def payload = ReportPayload.render(exporterRegistry, target.format, execution, reportData)
        def fileName = (target.config[CONFIG_FILENAME] as String)?.trim() ?: payload.fileName

        def root = new File(allowedRootDirectory).canonicalFile
        def targetDirectory = resolveWithin(root, directory)
        def targetFile = resolveWithin(targetDirectory, fileName)

        Files.createDirectories(targetDirectory.toPath())
        targetFile.bytes = payload.bytes

        LOG.info("distributed report {} to {}", execution.reportName, targetFile.absolutePath)
    }

    // Resolve child within base, rejecting anything that canonicalizes outside base (blocks .. and absolute escapes).
    private static File resolveWithin(File base, String child) {
        def candidate = new File(child)
        def resolved = (candidate.absolute ? candidate : new File(base, child)).canonicalFile

        if (resolved != base && !resolved.toPath().startsWith(base.toPath())) {
            throw new ReportException("path ${resolved} is outside allowed directory ${base}")
        }

        resolved
    }
}
