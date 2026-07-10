package be.orbinson.aem.groovy.console.queryaudit.spi;

import java.util.LinkedHashMap;
import java.util.Map;

import org.osgi.annotation.versioning.ProviderType;

/** A JCR query that was executed, with the Oak plan chosen for it on the live instance. */
@ProviderType
public final class AuditedQuery {

    private final String statement;
    private final String language;
    private final String plan;

    public AuditedQuery(String statement, String language, String plan) {
        this.statement = statement;
        this.language = language;
        this.plan = plan;
    }

    public String getStatement() {
        return statement;
    }

    /** The JCR query language of the statement, e.g. {@code JCR-SQL2} or {@code xpath}. */
    public String getLanguage() {
        return language;
    }

    public String getPlan() {
        return plan;
    }

    /** True when Oak found no covering index and fell back to a traversal. */
    public boolean isNeedsIndex() {
        return plan.toLowerCase().contains("traverse");
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("statement", statement);
        map.put("language", language);
        map.put("plan", plan);
        map.put("needsIndex", isNeedsIndex());
        return map;
    }
}
