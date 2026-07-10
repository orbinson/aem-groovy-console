package be.orbinson.aem.groovy.console.migration.healthcheck

import be.orbinson.aem.groovy.console.migration.MigrationConstants
import org.apache.felix.hc.api.HealthCheck
import org.apache.felix.hc.api.Result
import org.apache.sling.api.resource.LoginException
import org.apache.sling.api.resource.ResourceResolverFactory
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

/**
 * Verifies the migration extension itself is correctly set up, mirroring AECU's {@code SelfCheckHealthCheck}:
 * that its service user can log in and its repository root is reachable. Independent of any particular
 * migration run's outcome -- see {@link MigrationLastRunHealthCheck} for that.
 */
@Component(service = HealthCheck, property = [
        "hc.name=AEM Groovy Console Migration - Self Check",
        "hc.tags=migration"
])
class MigrationSelfCheckHealthCheck implements HealthCheck {

    @Reference
    private ResourceResolverFactory resourceResolverFactory

    @Override
    Result execute() {
        try {
            resourceResolverFactory.getServiceResourceResolver(null).withCloseable { resourceResolver ->
                if (resourceResolver.getResource(MigrationConstants.PATH_MIGRATION_ROOT)) {
                    new Result(Result.Status.OK, "migration service user and repository paths are reachable")
                } else {
                    new Result(Result.Status.CRITICAL,
                            "migration root path not found : ${MigrationConstants.PATH_MIGRATION_ROOT}")
                }
            }
        } catch (LoginException e) {
            new Result(Result.Status.CRITICAL, "unable to log in as the migration service user", e)
        } catch (Exception e) {
            new Result(Result.Status.HEALTH_CHECK_ERROR, "unexpected error while checking migration service health", e)
        }
    }
}
