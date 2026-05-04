package be.orbinson.aem.groovy.console.impl

import groovy.util.logging.Slf4j
import org.osgi.framework.BundleContext
import org.osgi.framework.Version
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component

/**
 * Workaround for SLING-13123: when Groovy bundles changed from bundles to fragments
 * (4.0.23+), the Sling installer's RestartActiveBundlesTask tries to restart them and fails.
 * Fixed in installer-core 3.14.6; this fixer is a no-op when that version (or higher) is present
 * or when the Sling installer is not running at all (e.g. AEMaaCS).
 */
@Component(
        service = GroovyBundleToFragmentFixer.class,
        immediate = true
)
@Slf4j("LOG")
class GroovyBundleToFragmentFixer {

    static final List<String> GROOVY_BUNDLES_TO_FRAGMENT = [
            "groovy-dateutil",
            "groovy-json",
            "groovy-nio",
            "groovy-templates",
            "groovy-xml",
            "groovy-yaml"
    ]

    static final String ENTITY_ID = "org.apache.sling.installer.core.restart.bundles:org.apache.sling.installer.core.restart.bundles"
    static final String SLING_INSTALLER_CORE_BUNDLE = "org.apache.sling.installer.core"
    static final Version FIXED_VERSION = new Version("3.14.6")

    @Activate
    protected void activate(BundleContext bundleContext) {
        try {
            if (isInstallerCoreFixed(bundleContext)) {
                LOG.debug("Sling Installer Core >= {}, skipping fragment fixer", FIXED_VERSION)
                return
            }
            def serviceRef = bundleContext.getServiceReference("org.apache.sling.installer.api.OsgiInstaller")
            if (serviceRef == null) {
                LOG.debug("OsgiInstaller service not available, skipping fragment fixer")
                return
            }
            try {
                def osgiInstaller = bundleContext.getService(serviceRef)
                fixGroovyBundlesToFragment(bundleContext, osgiInstaller)
            } finally {
                bundleContext.ungetService(serviceRef)
            }
        } catch (e) {
            LOG.warn("Could not fix the groovy bundles to fragments", e)
        }
    }

    private static boolean isInstallerCoreFixed(BundleContext bundleContext) {
        def installerBundle = bundleContext.bundles.find { it.symbolicName == SLING_INSTALLER_CORE_BUNDLE }
        installerBundle != null && installerBundle.version >= FIXED_VERSION
    }

    private static void fixGroovyBundlesToFragment(BundleContext bundleContext, def osgiInstaller) {
        def activeResource = osgiInstaller.persistentList
                ?.getEntityResourceList(ENTITY_ID)
                ?.activeResource

        if (activeResource != null) {
            def bundleIds = activeResource.getAttribute("bundles")
            if (bundleIds != null) {
                def fragmentIds = bundleIds
                        .collect { bundleId -> bundleContext.getBundle(bundleId) }
                        .findAll { bundle -> bundle != null && GROOVY_BUNDLES_TO_FRAGMENT.contains(bundle.symbolicName) }
                        .collect { bundle ->
                            LOG.info("Removing {} from RestartActiveBundlesTask; bundle switched to a fragment.", bundle.symbolicName)
                            bundle.symbolicName
                        } as Set

                bundleIds.removeAll(fragmentIds)
                activeResource.setAttribute("bundles", bundleIds)
                osgiInstaller.cleanupInstallableResources()
            }
        }
    }

}
