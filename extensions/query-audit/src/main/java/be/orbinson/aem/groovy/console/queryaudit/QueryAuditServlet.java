package be.orbinson.aem.groovy.console.queryaudit;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

import javax.jcr.Session;
import javax.servlet.Servlet;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import groovy.json.JsonOutput;

import be.orbinson.aem.groovy.console.GroovyConsoleService;
import be.orbinson.aem.groovy.console.api.context.impl.RequestScriptContext;
import be.orbinson.aem.groovy.console.configuration.ConfigurationService;
import be.orbinson.aem.groovy.console.queryaudit.spi.AuditResult;
import be.orbinson.aem.groovy.console.queryaudit.spi.AuditedQuery;
import be.orbinson.aem.groovy.console.queryaudit.spi.QueryAuditService;
import be.orbinson.aem.groovy.console.response.RunScriptResponse;

/**
 * Debugging endpoint that runs a Groovy Console script and reports, for every JCR query it executes, whether the live
 * Oak repository has an index that covers it. Same permission model as script execution.
 * <p>
 * {@code POST /bin/groovyconsole/query-audit} with either a {@code script} (source) or a {@code scriptPath} (JCR path
 * of a saved/deployed script). Returns JSON:
 * <pre>
 * { "output": "...", "exceptionStackTrace": "",
 *   "queries": [ { "statement": "...", "plan": "...", "needsIndex": true } ] }
 * </pre>
 * {@code needsIndex=true} means Oak had to traverse — no index on this instance covers that query.
 */
@Component(service = Servlet.class, immediate = true, property = {
        "sling.servlet.paths=/bin/groovyconsole/query-audit"
})
public class QueryAuditServlet extends SlingAllMethodsServlet {

    @Reference
    private transient ConfigurationService configurationService;

    @Reference
    private transient GroovyConsoleService consoleService;

    @Reference
    private transient QueryAuditService queryAuditService;

    @Override
    protected void doPost(SlingHttpServletRequest request, SlingHttpServletResponse response)
            throws ServletException, IOException {
        if (!configurationService.hasPermission(request)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        String script = getScript(request);
        if (script == null || script.isEmpty()) {
            writeError(response, "Script cannot be empty.");
            return;
        }

        Session session = request.getResourceResolver().adaptTo(Session.class);

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        RequestScriptContext scriptContext = new RequestScriptContext(
                request, response, outputStream, printStream(outputStream), script);

        // The console service runs a script from its content (there is no run-by-path API — the core
        // ScriptPostServlet loads by path the same way), so the audited work simply executes the script and its
        // RunScriptResponse comes back on the AuditResult, no mutable holder needed.
        AuditResult<RunScriptResponse> audit = queryAuditService.audit(session, () -> consoleService.runScript(scriptContext));

        writeReport(response, audit.getResult(), audit.getQueries());
    }

    private String getScript(SlingHttpServletRequest request) throws IOException {
        String scriptPath = request.getParameter("scriptPath");
        if (scriptPath != null && !scriptPath.isEmpty()) {
            return loadScript(request, scriptPath);
        }
        return request.getParameter("script");
    }

    /** Read the content of a saved/deployed script (an {@code nt:file}) from the repository. */
    private String loadScript(SlingHttpServletRequest request, String scriptPath) throws IOException {
        try (InputStream stream = request.getResourceResolver().getResource(scriptPath).adaptTo(InputStream.class);
             Scanner scanner = new Scanner(stream, StandardCharsets.UTF_8.name())) {
            return scanner.useDelimiter("\\A").hasNext() ? scanner.next() : "";
        }
    }

    private void writeReport(SlingHttpServletResponse response, RunScriptResponse runScriptResponse,
                             List<AuditedQuery> queries) throws IOException {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("output", runScriptResponse != null ? runScriptResponse.getOutput() : null);
        report.put("exceptionStackTrace", runScriptResponse != null ? runScriptResponse.getExceptionStackTrace() : null);
        List<Map<String, Object>> auditedQueries = new ArrayList<>();
        for (AuditedQuery query : queries) {
            auditedQueries.add(query.toMap());
        }
        report.put("queries", auditedQueries);
        write(response, JsonOutput.toJson(report));
    }

    private void writeError(SlingHttpServletResponse response, String message) throws IOException {
        write(response, JsonOutput.toJson(Collections.singletonMap("error", message)));
    }

    private void write(SlingHttpServletResponse response, String json) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        try (PrintWriter writer = response.getWriter()) {
            writer.write(json);
        }
    }

    private static PrintStream printStream(ByteArrayOutputStream outputStream) {
        try {
            return new PrintStream(outputStream, true, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }
}
