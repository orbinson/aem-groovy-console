package be.orbinson.aem.groovy.console.migration.impl

import be.orbinson.aem.groovy.console.migration.MigrationRunOptions
import be.orbinson.aem.groovy.console.migration.MigrationService
import org.apache.sling.api.resource.ResourceResolver
import org.apache.sling.api.resource.ResourceResolverFactory
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor

import javax.jcr.Node
import javax.jcr.Session

import static be.orbinson.aem.groovy.console.migration.MigrationConstants.TRIGGER_STARTUP
import static be.orbinson.aem.groovy.console.migration.impl.MigrationStartupHook.AutoRunMode
import static org.junit.jupiter.api.Assertions.assertEquals
import static org.junit.jupiter.api.Assertions.assertFalse
import static org.junit.jupiter.api.Assertions.assertTrue
import static org.mockito.ArgumentMatchers.any
import static org.mockito.ArgumentMatchers.eq
import static org.mockito.Mockito.mock
import static org.mockito.Mockito.never
import static org.mockito.Mockito.verify
import static org.mockito.Mockito.when

class MigrationStartupHookTest {

    @Test
    void cloudOnlyEnqueuesOnCompositeNodeStore() {
        def service = migrationService(false, ["/apps/groovyconsole-migration-scripts/001.groovy"])

        hook(service, resolver(true), AutoRunMode.CLOUD_ONLY).triggerStartupRun()

        def captor = ArgumentCaptor.forClass(MigrationRunOptions)
        verify(service).enqueue(captor.capture())
        assertEquals(TRIGGER_STARTUP, captor.value.trigger)
    }

    @Test
    void cloudOnlySkipsOnSingleNodeStore() {
        def service = migrationService(false, ["/x"])

        hook(service, resolver(false), AutoRunMode.CLOUD_ONLY).triggerStartupRun()

        verify(service, never()).enqueue(any())
    }

    @Test
    void alwaysEnqueuesOnSingleNodeStore() {
        def service = migrationService(false, ["/x"])

        hook(service, resolver(false), AutoRunMode.ALWAYS).triggerStartupRun()

        verify(service).enqueue(any())
    }

    @Test
    void skipsWhenNoPendingScripts() {
        def service = migrationService(false, [])

        hook(service, resolver(true), AutoRunMode.ALWAYS).triggerStartupRun()

        verify(service, never()).enqueue(any())
    }

    @Test
    void skipsWhenRunAlreadyInProgress() {
        def service = migrationService(true, ["/x"])

        hook(service, resolver(true), AutoRunMode.ALWAYS).triggerStartupRun()

        verify(service, never()).enqueue(any())
    }

    @Test
    void detectsCompositeAndSingleNodeStore() {
        def hook = new MigrationStartupHook()

        assertTrue(hook.isCompositeNodeStore(resolver(true)))
        assertFalse(hook.isCompositeNodeStore(resolver(false)))
    }

    @Test
    void permissionLimitedSessionIsNotClassifiedAsComposite() {
        // a session without general write permission also fails hasCapability, but must not be treated as cloud
        def session = mock(Session)
        def appsNode = mock(Node)

        when(session.getNode("/apps")).thenReturn(appsNode)
        when(session.hasPermission("/", Session.ACTION_SET_PROPERTY)).thenReturn(false)
        when(session.hasCapability(eq("addNode"), eq(appsNode), any(Object[]))).thenReturn(false)

        def resolver = mock(ResourceResolver)
        when(resolver.adaptTo(Session)).thenReturn(session)

        assertFalse(new MigrationStartupHook().isCompositeNodeStore(resolver))
    }

    private static MigrationService migrationService(boolean running, List<String> pending) {
        def service = mock(MigrationService)
        when(service.running).thenReturn(running)
        when(service.pendingScripts).thenReturn(pending)
        when(service.enqueue(any())).thenReturn("run-id")
        service
    }

    private static MigrationStartupHook hook(MigrationService service, ResourceResolver resolver, AutoRunMode mode) {
        def factory = mock(ResourceResolverFactory)
        when(factory.getServiceResourceResolver(null)).thenReturn(resolver)

        def hook = new MigrationStartupHook()
        hook.@migrationService = service
        hook.@resourceResolverFactory = factory
        hook.@autoRunMode = mode
        hook.@readinessTimeoutMillis = 5000L
        hook
    }

    private static ResourceResolver resolver(boolean composite) {
        def session = mock(Session)
        def appsNode = mock(Node)

        when(session.getNode("/apps")).thenReturn(appsNode)
        when(session.hasPermission("/", Session.ACTION_SET_PROPERTY)).thenReturn(true)
        // hasCapability returns false when the operation would fail -> immutable /apps -> composite (cloud) store
        when(session.hasCapability(eq("addNode"), eq(appsNode), any(Object[]))).thenReturn(!composite)

        def resolver = mock(ResourceResolver)
        when(resolver.adaptTo(Session)).thenReturn(session)
        resolver
    }
}
