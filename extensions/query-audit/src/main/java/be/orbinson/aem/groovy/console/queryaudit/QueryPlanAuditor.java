package be.orbinson.aem.groovy.console.queryaudit;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.jcr.Session;
import javax.jcr.query.Query;

import org.osgi.service.component.annotations.Component;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import be.orbinson.aem.groovy.console.queryaudit.spi.AuditResult;
import be.orbinson.aem.groovy.console.queryaudit.spi.AuditedQuery;
import be.orbinson.aem.groovy.console.queryaudit.spi.QueryAuditService;

/**
 * {@link QueryAuditService} implementation. Captures the statements Oak executes while running some work by listening
 * to Oak's query loggers, then re-runs {@code EXPLAIN} against the same session so the reported plan reflects the
 * instance's real indexes. Mirrors AEM's own "Explain Query" tool (a logback collector over the Oak query loggers),
 * with two refinements: capture is isolated to the calling thread via an MDC marker (so queries other requests run
 * concurrently do not pollute the result), and the QueryBuilder logger is included so scripts using
 * {@code createQuery} are covered.
 * <p>
 * Oak logs {@code query execute}/{@code query plan} at DEBUG behind an {@code isDebugEnabled()} guard, so the audited
 * loggers are briefly raised to DEBUG and restored afterwards.
 */
@Component(service = QueryAuditService.class)
public class QueryPlanAuditor implements QueryAuditService {

    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(QueryPlanAuditor.class);

    private static final String MDC_KEY = "groovyconsole.queryAudit";

    private static final String EXECUTE_PREFIX = "query execute ";

    private static final String INTERNAL_MARKER = "oak-internal";

    /** Oak logs an XPath query as its JCR-SQL2 conversion with the original appended as a trailing comment. */
    private static final Pattern XPATH_COMMENT = Pattern.compile("/\\* xpath: (.*) \\*/\\s*$", Pattern.DOTALL);

    /** Oak's query engine logger and AEM's QueryBuilder logger. */
    private static final List<String> QUERY_LOGGERS = Arrays.asList(
            "org.apache.jackrabbit.oak.query.QueryImpl",
            "com.day.cq.search.impl.builder.QueryImpl");

    @Override
    public <T> AuditResult<T> audit(Session session, Supplier<T> work) {
        if (!(LoggerFactory.getILoggerFactory() instanceof LoggerContext)) {
            // Query capture needs the logback backend (as in AEM/Sling). Degrade gracefully: run the work anyway.
            LOG.warn("SLF4J backend is not logback; running without query capture");
            return new AuditResult<>(work.get(), new ArrayList<>());
        }

        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        String marker = UUID.randomUUID().toString();

        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.setContext(loggerContext);
        appender.start();

        // A logback logger with no explicitly-assigned level returns null from getLevel() (it inherits); restoring
        // setLevel(null) simply reverts to inheriting. A plain map is fine since it may hold null values.
        Map<String, Level> previousLevels = new LinkedHashMap<>();
        List<Logger> loggers = new ArrayList<>();
        for (String name : QUERY_LOGGERS) {
            Logger logger = loggerContext.getLogger(name);
            previousLevels.put(name, logger.getLevel());
            logger.setLevel(Level.DEBUG);
            logger.addAppender(appender);
            loggers.add(logger);
        }

        T workResult;
        MDC.put(MDC_KEY, marker);
        try {
            workResult = work.get();
        } finally {
            MDC.remove(MDC_KEY);
            for (Logger logger : loggers) {
                logger.detachAppender(appender);
                logger.setLevel(previousLevels.get(logger.getName()));
            }
            appender.stop();
        }

        List<AuditedQuery> results = new ArrayList<>();
        for (String statement : collectStatements(appender.list, marker)) {
            String language = isSql2(statement) ? Query.JCR_SQL2 : Query.XPATH;
            String plan = explain(session, statement, language);
            // Report a converted XPath query as its original statement (the plan is the same either way).
            Matcher xpath = XPATH_COMMENT.matcher(statement);
            if (xpath.find()) {
                results.add(new AuditedQuery(xpath.group(1), Query.XPATH, plan));
            } else {
                results.add(new AuditedQuery(statement, language, plan));
            }
        }
        return new AuditResult<>(workResult, results);
    }

    /** Distinct, non-internal statements from this thread's captured "query execute" events, in execution order. */
    private static Set<String> collectStatements(List<ILoggingEvent> events, String marker) {
        Set<String> statements = new LinkedHashSet<>();
        for (ILoggingEvent event : events) {
            if (marker.equals(event.getMDCPropertyMap().get(MDC_KEY))) {
                String message = event.getFormattedMessage();
                if (message.startsWith(EXECUTE_PREFIX)) {
                    String statement = message.substring(EXECUTE_PREFIX.length()).trim();
                    if (!statement.contains(INTERNAL_MARKER)) {
                        statements.add(statement);
                    }
                }
            }
        }
        return statements;
    }

    /**
     * EXPLAIN a captured statement in its own language and return the plan Oak chose. Oak normally logs executed
     * statements as JCR-SQL2 (it converts XPath internally), but AEM's QueryBuilder logs XPath, so the language is
     * detected rather than assumed — and the reported statement/plan are then in whatever language Oak executed.
     */
    private static String explain(Session session, String statement, String language) {
        try {
            return session.getWorkspace().getQueryManager()
                    .createQuery("explain " + statement, language)
                    .execute().getRows().nextRow().getValue("plan").getString();
        } catch (Exception e) {
            LOG.warn("could not explain {} query: {}", language, statement, e);
            return "explain failed: " + e.getMessage();
        }
    }

    /** JCR-SQL2 statements start with select/explain/measure; anything else (e.g. {@code /jcr:root...}) is XPath. */
    private static boolean isSql2(String statement) {
        String trimmed = statement.trim();
        return trimmed.regionMatches(true, 0, "select", 0, 6)
                || trimmed.regionMatches(true, 0, "explain", 0, 7)
                || trimmed.regionMatches(true, 0, "measure", 0, 7);
    }
}
