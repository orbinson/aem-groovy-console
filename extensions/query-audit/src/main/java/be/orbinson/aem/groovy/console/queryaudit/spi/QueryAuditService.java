package be.orbinson.aem.groovy.console.queryaudit.spi;

import java.util.function.Supplier;

import javax.jcr.Session;

import org.osgi.annotation.versioning.ProviderType;

/**
 * Runs a unit of work and reports, per JCR query it executed, whether the live Oak repository has an index that covers
 * it. Registered by the (optional) query-audit extension; consumers reference it optionally so they work with or
 * without the extension installed.
 */
@ProviderType
public interface QueryAuditService {

    /**
     * Run {@code work}, capturing the queries it executes, and EXPLAIN each against the given session.
     *
     * @param session session used to EXPLAIN the captured statements (typically the request's session)
     * @param work the work to run (e.g. executing a script); its return value is passed through on the result
     * @param <T> type of value the work produces
     * @return the work's result together with one audited query per distinct executed statement
     */
    <T> AuditResult<T> audit(Session session, Supplier<T> work);
}
