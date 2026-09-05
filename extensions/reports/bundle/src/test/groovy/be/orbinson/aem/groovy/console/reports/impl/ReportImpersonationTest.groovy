package be.orbinson.aem.groovy.console.reports.impl

import be.orbinson.aem.groovy.console.reports.ReportException
import org.apache.sling.api.resource.LoginException
import org.apache.sling.api.resource.ResourceResolver
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.assertFalse
import static org.junit.jupiter.api.Assertions.assertSame
import static org.junit.jupiter.api.Assertions.assertThrows
import static org.junit.jupiter.api.Assertions.assertTrue

class ReportImpersonationTest {

    private static ResourceResolver executor(Closure cloneImpl = { Map m -> throw new LoginException("denied") }) {
        [getUserID: { "reports-executor" }, clone: cloneImpl] as ResourceResolver
    }

    @Test
    void "runResolver returns the executor itself when no run-as is configured"() {
        def executorResolver = executor()

        assertSame(executorResolver, ReportImpersonation.runResolver(executorResolver, null))
        assertSame(executorResolver, ReportImpersonation.runResolver(executorResolver, ""))
        assertSame(executorResolver, ReportImpersonation.runResolver(executorResolver, "reports-executor"))
        assertFalse(ReportImpersonation.isImpersonated(executorResolver, executorResolver))
    }

    @Test
    void "runResolver impersonates the configured run-as user"() {
        def impersonated = [getUserID: { "reporter" }] as ResourceResolver
        def executorResolver = executor({ Map m -> impersonated })

        def runResolver = ReportImpersonation.runResolver(executorResolver, "reporter")

        assertSame(impersonated, runResolver)
        assertTrue(ReportImpersonation.isImpersonated(executorResolver, runResolver))
    }

    @Test
    void "runResolver fails closed when the executor may not impersonate the run-as"() {
        def executorResolver = executor() // default clone throws LoginException

        assertThrows(ReportException) {
            ReportImpersonation.runResolver(executorResolver, "reporter")
        }
    }
}
