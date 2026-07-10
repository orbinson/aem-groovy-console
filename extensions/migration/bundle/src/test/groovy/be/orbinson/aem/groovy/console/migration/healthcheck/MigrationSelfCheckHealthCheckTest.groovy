package be.orbinson.aem.groovy.console.migration.healthcheck

import be.orbinson.aem.groovy.console.migration.MigrationConstants
import io.wcm.testing.mock.aem.junit5.AemContext
import io.wcm.testing.mock.aem.junit5.AemContextExtension
import org.apache.felix.hc.api.Result
import org.apache.sling.api.resource.LoginException
import org.apache.sling.api.resource.ResourceResolverFactory
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

import static org.junit.jupiter.api.Assertions.assertEquals
import static org.mockito.Mockito.mock
import static org.mockito.Mockito.when

@ExtendWith(AemContextExtension.class)
class MigrationSelfCheckHealthCheckTest {

    private final AemContext context = new AemContext()

    @Test
    void okWhenServiceUserCanLogInAndRootPathExists() {
        context.build().resource(MigrationConstants.PATH_MIGRATION_ROOT).commit()

        def healthCheck = context.registerInjectActivateService(new MigrationSelfCheckHealthCheck())

        def result = healthCheck.execute()

        assertEquals(Result.Status.OK, result.status)
    }

    @Test
    void criticalWhenRootPathDoesNotExist() {
        def healthCheck = context.registerInjectActivateService(new MigrationSelfCheckHealthCheck())

        def result = healthCheck.execute()

        assertEquals(Result.Status.CRITICAL, result.status)
    }

    @Test
    void criticalWhenServiceUserLoginFails() {
        def resourceResolverFactory = mock(ResourceResolverFactory)
        when(resourceResolverFactory.getServiceResourceResolver(null)).thenThrow(new LoginException("no such service user"))

        def healthCheck = new MigrationSelfCheckHealthCheck(resourceResolverFactory: resourceResolverFactory)

        def result = healthCheck.execute()

        assertEquals(Result.Status.CRITICAL, result.status)
    }

    @Test
    void healthCheckErrorOnUnexpectedException() {
        def resourceResolverFactory = mock(ResourceResolverFactory)
        when(resourceResolverFactory.getServiceResourceResolver(null)).thenThrow(new RuntimeException("boom"))

        def healthCheck = new MigrationSelfCheckHealthCheck(resourceResolverFactory: resourceResolverFactory)

        def result = healthCheck.execute()

        assertEquals(Result.Status.HEALTH_CHECK_ERROR, result.status)
    }
}
