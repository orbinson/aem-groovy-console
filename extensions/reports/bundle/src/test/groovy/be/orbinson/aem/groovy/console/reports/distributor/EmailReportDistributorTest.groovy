package be.orbinson.aem.groovy.console.reports.distributor

import be.orbinson.aem.groovy.console.reports.ReportException
import be.orbinson.aem.groovy.console.reports.ReportExporterRegistry
import be.orbinson.aem.groovy.console.reports.data.ReportData
import be.orbinson.aem.groovy.console.reports.exporter.CsvReportExporter
import be.orbinson.aem.groovy.console.reports.model.ReportDistributionTarget
import be.orbinson.aem.groovy.console.reports.model.ReportExecution
import be.orbinson.aem.groovy.console.reports.model.ReportExecutionStatus
import com.day.cq.mailer.MailService
import org.apache.commons.mail.Email
import org.apache.commons.mail.MultiPartEmail
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.assertEquals
import static org.junit.jupiter.api.Assertions.assertInstanceOf
import static org.junit.jupiter.api.Assertions.assertThrows

class EmailReportDistributorTest {

    private static final ReportExporterRegistry EXPORTER_REGISTRY = [
            getExporter : { String format -> format == "csv" ? new CsvReportExporter() : null },
            getExporters: { [new CsvReportExporter()] }
    ] as ReportExporterRegistry

    private static ReportData reportData() {
        def data = new ReportData()
        data.column("Name")
        data.row("Home")
        data
    }

    private static ReportExecution execution() {
        new ReportExecution(reportName: "traffic", status: ReportExecutionStatus.SUCCESS, finishedAt: Calendar.instance)
    }

    private static ReportDistributionTarget target(Map config) {
        new ReportDistributionTarget(distributorId: "email", format: "csv", config: config)
    }

    private static EmailReportDistributor distributor(List<Email> sent) {
        def distributor = new EmailReportDistributor()
        distributor.exporterRegistry = EXPORTER_REGISTRY
        distributor.mailService = [send: { Email email -> sent << email; null }] as MailService
        distributor
    }

    @Test
    void "sends an email with the recipients from a comma-separated string"() {
        def sent = []
        def distributor = distributor(sent)

        distributor.distribute(execution(), reportData(), target([recipients: "a@example.com, b@example.com"]))

        assertEquals(1, sent.size())
        def email = assertInstanceOf(MultiPartEmail, sent[0])
        assertEquals(2, email.toAddresses.size())
    }

    @Test
    void "sends an email with the recipients from a list"() {
        def sent = []
        def distributor = distributor(sent)

        distributor.distribute(execution(), reportData(), target([recipients: ["a@example.com"]]))

        assertEquals(1, sent.size())
        assertEquals("a@example.com", (sent[0] as MultiPartEmail).toAddresses[0].address)
    }

    @Test
    void "fails when the mail service is unavailable"() {
        def distributor = new EmailReportDistributor()
        distributor.exporterRegistry = EXPORTER_REGISTRY

        assertThrows(ReportException) {
            distributor.distribute(execution(), reportData(), target([recipients: "a@example.com"]))
        }
    }

    @Test
    void "fails when there are no recipients"() {
        def distributor = distributor([])

        assertThrows(ReportException) {
            distributor.distribute(execution(), reportData(), target([:]))
        }
    }

    @Test
    void "sends when every recipient domain is on the allowlist"() {
        def sent = []
        def distributor = distributor(sent)
        distributor.allowedRecipientDomains = ["example.com"] as Set

        distributor.distribute(execution(), reportData(), target([recipients: "a@example.com, b@EXAMPLE.com"]))

        assertEquals(1, sent.size())
    }

    @Test
    void "rejects a recipient whose domain is not on the allowlist"() {
        def sent = []
        def distributor = distributor(sent)
        distributor.allowedRecipientDomains = ["example.com"] as Set

        assertThrows(ReportException) {
            distributor.distribute(execution(), reportData(),
                    target([recipients: "a@example.com, evil@attacker.com"]))
        }
        assertEquals(0, sent.size())
    }

    @Test
    void "allowlist check permits anything when empty and enforces domains when set"() {
        EmailReportDistributor.assertRecipientsAllowed(["a@anywhere.com"] as Set, [] as Set)
        EmailReportDistributor.assertRecipientsAllowed(["a@example.com"] as Set, ["example.com"] as Set)

        assertThrows(ReportException) {
            EmailReportDistributor.assertRecipientsAllowed(["a@other.com"] as Set, ["example.com"] as Set)
        }
    }
}
