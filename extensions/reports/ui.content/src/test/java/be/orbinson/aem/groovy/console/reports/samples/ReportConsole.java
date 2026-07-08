package be.orbinson.aem.groovy.console.reports.samples;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.apache.sling.testing.mock.sling.context.SlingContextImpl;

import be.orbinson.aem.groovy.console.GroovyConsoleService;
import be.orbinson.aem.groovy.console.reports.impl.DefaultReportScriptContext;
import be.orbinson.aem.groovy.console.response.RunScriptResponse;

/**
 * Runs a report script the way the reports extension does at runtime: through a {@link DefaultReportScriptContext}
 * so the {@code report} and {@code params} bindings are injected by the {@code ReportBindingExtensionProvider}.
 * <p>
 * The context must have a {@link GroovyConsoleService} registered (via {@code ContextPlugins.GROOVY_CONSOLE}) and
 * the {@code ReportBindingExtensionProvider} registered so those report bindings resolve.
 */
final class ReportConsole {

    private ReportConsole() {
    }

    static RunScriptResponse runReport(SlingContextImpl context, String script, Map<String, Object> parameters) {
        GroovyConsoleService service = context.getService(GroovyConsoleService.class);

        if (service == null) {
            throw new IllegalStateException("GroovyConsoleService is not registered. Add ContextPlugins.GROOVY_CONSOLE "
                    + "to your context builder.");
        }

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        DefaultReportScriptContext scriptContext = new DefaultReportScriptContext();
        scriptContext.setReportName("test-report");
        scriptContext.setParameterValues(parameters);
        scriptContext.setResourceResolver(context.resourceResolver());
        scriptContext.setOutputStream(outputStream);
        scriptContext.setPrintStream(printStream(outputStream));
        scriptContext.setScript(script);
        scriptContext.setUserId(context.resourceResolver().getUserID());

        return service.runScript(scriptContext);
    }

    private static PrintStream printStream(ByteArrayOutputStream outputStream) {
        try {
            return new PrintStream(outputStream, true, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }
}
