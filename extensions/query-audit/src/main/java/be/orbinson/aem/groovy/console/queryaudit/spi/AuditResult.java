package be.orbinson.aem.groovy.console.queryaudit.spi;

import java.util.List;

import org.osgi.annotation.versioning.ProviderType;

/**
 * Outcome of {@link QueryAuditService#audit}: the value the audited work produced, plus the queries it executed and
 * the Oak plan chosen for each. Bundling the work's result here lets callers avoid a mutable holder when they need
 * both the result and the audit.
 *
 * @param <T> type of value the audited work produced
 */
@ProviderType
public final class AuditResult<T> {

    private final T result;
    private final List<AuditedQuery> queries;

    public AuditResult(T result, List<AuditedQuery> queries) {
        this.result = result;
        this.queries = queries;
    }

    /** The value returned by the audited work (may be {@code null} if the work produced none). */
    public T getResult() {
        return result;
    }

    /** One entry per distinct query the work executed, in execution order. */
    public List<AuditedQuery> getQueries() {
        return queries;
    }
}
