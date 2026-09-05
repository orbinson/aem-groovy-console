package be.orbinson.aem.groovy.console.api

import groovy.json.JsonSlurper
import groovy.util.logging.Slf4j
import org.apache.sling.api.resource.ResourceResolver

/**
 * Resolves content-hashed SPA asset URLs from a Vite build manifest (<code>manifest.json</code> at the spa root),
 * so console pages can link the exact hashed file: an unchanged asset keeps its URL and stays cached, a changed
 * one gets a fresh URL and is refetched. Every lookup falls back gracefully (returns null / the input URL) when no
 * manifest or matching entry is present, so a build without a manifest keeps working with stable names.
 */
@Slf4j("LOG")
class SpaManifest {

    private static final String MANIFEST_NAME = "manifest.json"

    private static final String ASSETS_MARKER = "/spa/assets/"

    private SpaManifest() {
    }

    /**
     * Resolve the hashed js + css for a manifest entry, matched by its Vite chunk {@code name} (e.g. "index",
     * "reports", "monaco"), under a spa base path such as {@code /apps/groovyconsole/spa}.
     *
     * @return map with {@code js} (absolute path or null) and {@code css} (list of absolute paths, possibly empty)
     */
    static Map<String, Object> entry(ResourceResolver resourceResolver, String spaBasePath, String name) {
        def value = find(resourceResolver, spaBasePath, name)

        if (!value) {
            return [js: null, css: []] as Map<String, Object>
        }

        [js : value.file ? "${spaBasePath}/${value.file}".toString() : null,
         css: (value.css ?: []).collect { css -> "${spaBasePath}/${css}".toString() }] as Map<String, Object>
    }

    /**
     * Resolve a stable module URL (e.g. {@code /apps/groovyconsole-reports/spa/assets/reports-panel.js}) to its
     * content-hashed URL via that spa's manifest.  Returns the input unchanged when it can't be resolved.
     */
    static String resolveModuleUrl(ResourceResolver resourceResolver, String moduleUrl) {
        def index = moduleUrl == null ? -1 : moduleUrl.indexOf(ASSETS_MARKER)

        if (index < 0) {
            return moduleUrl
        }

        def spaBasePath = moduleUrl.substring(0, index + "/spa".length())
        def name = moduleUrl.substring(index + ASSETS_MARKER.length()).replaceFirst(/\.js$/, "")
        def value = find(resourceResolver, spaBasePath, name)

        value?.file ? "${spaBasePath}/${value.file}".toString() : moduleUrl
    }

    private static Map find(ResourceResolver resourceResolver, String spaBasePath, String name) {
        try {
            def resource = resourceResolver?.getResource("${spaBasePath}/${MANIFEST_NAME}")
            def stream = resource?.adaptTo(InputStream)

            if (!stream) {
                return null
            }

            def manifest = stream.withCloseable { new JsonSlurper().parse(it) }

            manifest.values().find { value -> value instanceof Map && value.name == name } as Map
        } catch (Exception e) {
            LOG.warn("could not read SPA manifest under {}", spaBasePath, e)

            null
        }
    }
}
