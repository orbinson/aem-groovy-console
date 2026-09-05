package be.orbinson.aem.groovy.console.reports.impl

import be.orbinson.aem.groovy.console.reports.ReportDistributor
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.assertEquals
import static org.junit.jupiter.api.Assertions.assertNull

class DefaultReportDistributorRegistryTest {

    private static ReportDistributor distributor(String id) {
        [getId: { id }, getName: { id.capitalize() }, distribute: { a, b, c -> }] as ReportDistributor
    }

    @Test
    void "lists distributors sorted by id and looks them up by id"() {
        def registry = new DefaultReportDistributorRegistry()
        def email = distributor("email")
        def filesystem = distributor("filesystem")

        registry.bindReportDistributor(filesystem)
        registry.bindReportDistributor(email)

        assertEquals(["email", "filesystem"], registry.distributors*.id)
        assertEquals(email, registry.getDistributor("email"))
        assertNull(registry.getDistributor("unknown"))
    }

    @Test
    void "unbinding removes a distributor"() {
        def registry = new DefaultReportDistributorRegistry()
        def email = distributor("email")

        registry.bindReportDistributor(email)
        registry.unbindReportDistributor(email)

        assertEquals([], registry.distributors)
        assertNull(registry.getDistributor("email"))
    }
}
