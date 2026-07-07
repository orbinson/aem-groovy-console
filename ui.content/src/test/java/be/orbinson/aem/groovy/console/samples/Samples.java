package be.orbinson.aem.groovy.console.samples;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Reads a shipped sample script from this module's content so tests exercise the exact {@code .groovy} that is deployed.
 */
final class Samples {

    private static final Path DIR =
            Path.of("src/main/content/jcr_root/conf/groovyconsole/scripts/samples");

    private Samples() {
    }

    static String read(String fileName) {
        try {
            return Files.readString(DIR.resolve(fileName), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
