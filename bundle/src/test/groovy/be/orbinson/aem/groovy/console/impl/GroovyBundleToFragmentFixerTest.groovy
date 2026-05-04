package be.orbinson.aem.groovy.console.impl

import org.junit.jupiter.api.Test
import org.osgi.framework.BundleContext

import static org.mockito.Mockito.*

class GroovyBundleToFragmentFixerTest {

    @Test
    void "skips when OsgiInstaller is not present"() {
        def bundleContext = mock(BundleContext)
        when(bundleContext.bundles).thenReturn(new org.osgi.framework.Bundle[0])
        when(bundleContext.getServiceReference("org.apache.sling.installer.api.OsgiInstaller")).thenReturn(null)

        new GroovyBundleToFragmentFixer().activate(bundleContext)

        verify(bundleContext).getServiceReference("org.apache.sling.installer.api.OsgiInstaller")
    }
}
