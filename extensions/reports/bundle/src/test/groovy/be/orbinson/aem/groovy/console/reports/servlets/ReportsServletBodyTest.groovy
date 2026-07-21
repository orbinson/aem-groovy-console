package be.orbinson.aem.groovy.console.reports.servlets

import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.assertEquals
import static org.junit.jupiter.api.Assertions.assertFalse
import static org.junit.jupiter.api.Assertions.assertNull
import static org.junit.jupiter.api.Assertions.assertTrue

class ReportsServletBodyTest {

    @Test
    void "parses schedule from the request body, ignoring any client-supplied runAs and scheduledBy"() {
        def schedule = ReportsServlet.scheduleFromBody([
                enabled        : true,
                cronExpression : "0 0 6 * * ?",
                runAs          : "attacker", // must be ignored — forced to the requesting user server-side
                scheduledBy    : "attacker", // must be ignored — set server-side
                parameterValues: [region: "eu", limit: 10]
        ])

        assertTrue(schedule.enabled)
        assertEquals("0 0 6 * * ?", schedule.cronExpression)
        assertNull(schedule.runAs, "runAs must never be taken from the request body")
        assertNull(schedule.scheduledBy, "scheduledBy must never be taken from the request body")
        assertEquals(["region": "eu", "limit": "10"], schedule.parameterValues)
    }

    @Test
    void "defaults schedule fields when omitted"() {
        def schedule = ReportsServlet.scheduleFromBody([:])

        assertFalse(schedule.enabled)
        assertNull(schedule.cronExpression)
        assertNull(schedule.runAs)
        assertEquals([:], schedule.parameterValues)
    }

    @Test
    void "parses a distribution target with its config"() {
        def target = ReportsServlet.distributionFromBody([
                distributorId: "email",
                format       : "xlsx",
                config       : [recipients: "a@example.com, b@example.com", subject: "Weekly"]
        ])

        assertEquals("email", target.distributorId)
        assertEquals("xlsx", target.format)
        assertEquals("a@example.com, b@example.com", target.config["recipients"])
        assertEquals("Weekly", target.config["subject"])
    }

    @Test
    void "fromBody includes schedule and distributions"() {
        def definition = ReportsServlet.fromBody([
                name         : "traffic",
                script       : "report.data()",
                schedule     : [enabled: true, cronExpression: "0 0 * * * ?"],
                distributions: [
                        [distributorId: "filesystem", format: "csv", config: [directory: "/var/reports"]]
                ]
        ])

        assertEquals("traffic", definition.name)
        assertTrue(definition.schedule.enabled)
        assertEquals(1, definition.distributions.size())
        assertEquals("filesystem", definition.distributions[0].distributorId)
    }

    @Test
    void "fromBody leaves schedule null when absent"() {
        def definition = ReportsServlet.fromBody([name: "traffic", script: "report.data()"])

        assertNull(definition.schedule)
        assertEquals([], definition.distributions)
    }
}
