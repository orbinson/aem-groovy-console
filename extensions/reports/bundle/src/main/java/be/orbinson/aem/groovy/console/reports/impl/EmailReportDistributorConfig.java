package be.orbinson.aem.groovy.console.reports.impl;

import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

@ObjectClassDefinition(name = "AEM Groovy Console Reports - Email Distributor",
        description = "Distributes report results by email via the AEM mail service. Optionally restrict where "
                + "reports may be sent with a recipient-domain allowlist.")
public @interface EmailReportDistributorConfig {

    @AttributeDefinition(name = "Allowed recipient domains",
            description = "Optional allowlist of recipient email domains (e.g. example.com). When empty, reports "
                    + "may be sent to any address. When set, every recipient's domain must match one of these "
                    + "entries or the distribution is rejected.")
    String[] allowedRecipientDomains() default {};
}
