package be.orbinson.aem.groovy.console.reports.distributor

import be.orbinson.aem.groovy.console.reports.ReportDistributor
import be.orbinson.aem.groovy.console.reports.ReportException
import be.orbinson.aem.groovy.console.reports.ReportExporterRegistry
import be.orbinson.aem.groovy.console.reports.data.ReportData
import be.orbinson.aem.groovy.console.reports.impl.EmailReportDistributorConfig
import be.orbinson.aem.groovy.console.reports.model.ReportDistributionTarget
import be.orbinson.aem.groovy.console.reports.model.ReportExecution
import com.day.cq.mailer.MailService
import groovy.util.logging.Slf4j
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Modified
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ReferenceCardinality
import org.osgi.service.component.annotations.ReferencePolicy
import org.apache.commons.mail.EmailAttachment
import org.apache.commons.mail.MultiPartEmail
import org.osgi.service.metatype.annotations.Designate

import javax.mail.util.ByteArrayDataSource
import java.nio.charset.StandardCharsets

/**
 * Distributes report results by email, attaching the rendered export.  Uses the AEM Day CQ mail service, whose SMTP
 * settings are configured on the platform; on plain Sling (no mail service) this component stays inactive.
 *
 * <p>An optional recipient-domain allowlist bounds where reports may be sent; when it is empty reports may go to
 * any address.
 */
@Component(service = ReportDistributor, immediate = true)
@Designate(ocd = EmailReportDistributorConfig)
@Slf4j("LOG")
class EmailReportDistributor implements ReportDistributor {

    static final String ID = "email"

    /** Target config key: recipients, either a list or a comma/semicolon/whitespace separated string. */
    static final String CONFIG_RECIPIENTS = "recipients"

    /** Target config key: optional subject line. */
    static final String CONFIG_SUBJECT = "subject"

    static final String DEFAULT_SUBJECT = "AEM Groovy Console Report"

    @Reference
    private ReportExporterRegistry exporterRegistry

    @Reference(cardinality = ReferenceCardinality.OPTIONAL, policy = ReferencePolicy.DYNAMIC)
    private volatile MailService mailService

    private volatile Set<String> allowedRecipientDomains = [] as Set

    @Activate
    @Modified
    void activate(EmailReportDistributorConfig config) {
        allowedRecipientDomains = (config.allowedRecipientDomains() ?: new String[0])
                .collect { it?.trim()?.toLowerCase() }
                .findAll { it } as Set
    }

    @Override
    String getId() {
        ID
    }

    @Override
    String getName() {
        "Email"
    }

    @Override
    void distribute(ReportExecution execution, ReportData reportData, ReportDistributionTarget target) {
        if (!mailService) {
            throw new ReportException("mail service unavailable")
        }

        def recipients = recipients(target)

        if (!recipients) {
            throw new ReportException("email distribution target has no recipients configured")
        }

        assertRecipientsAllowed(recipients, allowedRecipientDomains)

        def payload = ReportPayload.render(exporterRegistry, target.format, execution, reportData)
        def subject = (target.config[CONFIG_SUBJECT] as String)?.trim() ?: DEFAULT_SUBJECT

        def email = new MultiPartEmail()

        recipients.each { recipient -> email.addTo(recipient) }

        email.subject = subject
        email.charset = StandardCharsets.UTF_8.name()
        email.setMsg(body(execution))
        email.attach(new ByteArrayDataSource(payload.bytes, payload.contentType), payload.fileName,
                "Report ${execution.reportName}", EmailAttachment.ATTACHMENT)

        LOG.debug("sending report {} to {}", execution.reportName, recipients)

        mailService.send(email)
    }

    // Reject the whole distribution if any recipient falls outside the configured domain allowlist. An empty
    // allowlist means unrestricted. Fail rather than silently drop recipients so a misconfiguration is visible.
    static void assertRecipientsAllowed(Set<String> recipients, Set<String> allowedDomains) {
        if (!allowedDomains) {
            return
        }

        def rejected = recipients.findAll { recipient ->
            def at = recipient.lastIndexOf("@")
            def domain = at >= 0 ? recipient.substring(at + 1).trim().toLowerCase() : ""

            !domain || !allowedDomains.contains(domain)
        }

        if (rejected) {
            throw new ReportException("recipient(s) not permitted by the email domain allowlist: " +
                    "${rejected.join(", ")}")
        }
    }

    private static Set<String> recipients(ReportDistributionTarget target) {
        def value = target.config[CONFIG_RECIPIENTS]

        if (value instanceof Collection) {
            return value.collect { it as String }.findAll { it?.trim() }*.trim() as Set
        }

        if (value instanceof String) {
            return value.split("[,;\\s]+").findAll { it } as Set
        }

        [] as Set
    }

    private static String body(ReportExecution execution) {
        "Report '${execution.reportName}' completed with status ${execution.status}. See the attached ${execution.reportName} export."
    }
}
