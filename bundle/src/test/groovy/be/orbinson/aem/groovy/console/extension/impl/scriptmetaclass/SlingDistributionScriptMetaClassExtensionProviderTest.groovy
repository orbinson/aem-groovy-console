package be.orbinson.aem.groovy.console.extension.impl.scriptmetaclass

import be.orbinson.aem.groovy.console.api.context.ScriptContext
import io.wcm.testing.mock.aem.junit5.AemContext
import io.wcm.testing.mock.aem.junit5.AemContextExtension
import org.apache.sling.distribution.DistributionRequest
import org.apache.sling.distribution.DistributionRequestType
import org.apache.sling.distribution.Distributor
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor

import static org.junit.jupiter.api.Assertions.assertEquals
import static org.junit.jupiter.api.Assertions.assertFalse
import static org.junit.jupiter.api.Assertions.assertTrue
import static org.junit.jupiter.api.Assumptions.assumeTrue
import static org.mockito.ArgumentMatchers.eq
import static org.mockito.Mockito.mock
import static org.mockito.Mockito.verify
import static org.mockito.Mockito.when

@ExtendWith(AemContextExtension)
class SlingDistributionScriptMetaClassExtensionProviderTest {

    private final AemContext context = new AemContext()

    private final Distributor distributor = mock(Distributor)

    private DistributionRequest runAndCapture(String scriptText, String expectedAgent) {
        def resourceResolver = context.resourceResolver()
        def provider = new SlingDistributionScriptMetaClassExtensionProvider()
        provider.distributor = distributor

        def scriptContext = mock(ScriptContext)
        when(scriptContext.resourceResolver).thenReturn(resourceResolver)

        def script = new GroovyShell().parse(scriptText)
        script.metaClass(provider.getScriptMetaClass(scriptContext))
        script.run()

        def captor = ArgumentCaptor.forClass(DistributionRequest)
        verify(distributor).distribute(eq(expectedAgent), eq(resourceResolver), captor.capture())
        captor.value
    }

    @Test
    void distributeUsesDefaults() {
        def request = runAndCapture("distribute('/content/test')", "publish")

        assertEquals(DistributionRequestType.ADD, request.requestType)
        assertEquals(["/content/test"], request.paths as List)
        assertFalse(request.isDeep("/content/test"))
    }

    @Test
    void distributeWithAgentAndDeep() {
        def request = runAndCapture("distribute('/content/test', 'preview', true)", "preview")

        assertEquals(DistributionRequestType.ADD, request.requestType)
        assertTrue(request.isDeep("/content/test"))
    }

    @Test
    void invalidateUsesDefaults() {
        // INVALIDATE only exists in the AEMaaCS distribution API, not in the AEM 6.5 uber-jar this module compiles against
        assumeTrue(DistributionRequestType.values()*.name().contains("INVALIDATE"))

        def request = runAndCapture("invalidate('/content/test')", "publish")

        assertEquals(DistributionRequestType.INVALIDATE, request.requestType)
        assertEquals(["/content/test"], request.paths as List)
    }
}
