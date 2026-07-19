package be.orbinson.aem.groovy.console.reports.samples;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Reads a shipped report's {@code .groovy} script from this module's content so tests exercise the exact script
 * that is deployed. Reports store their script as a {@code <report name>/<report name>.groovy} file node.
 */
final class Reports {

    private static final Path DIR = Path.of("src/main/content/jcr_root/conf/groovyconsole/reports");

    private Reports() {
    }

    static String readScript(String reportName) {
        try {
            return Files.readString(DIR.resolve(reportName).resolve(reportName + ".groovy"), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
