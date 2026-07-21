package be.orbinson.aem.groovy.console.testing;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

import org.apache.sling.testing.mock.sling.context.SlingContextImpl;

import be.orbinson.aem.groovy.console.GroovyConsoleService;
import be.orbinson.aem.groovy.console.api.context.impl.RequestScriptContext;
import be.orbinson.aem.groovy.console.response.RunScriptResponse;

/**
 * Convenience for executing a Groovy Console script against a context with a {@link GroovyConsoleService} registered
 */
public final class GroovyConsole {

    private GroovyConsole() {
    }

    /**
     * Execute a script against the given context and return the response (output, result, exception stack trace,
     * running time). The context must have a {@link GroovyConsoleService} registered, for example by building it
     * with {@link ContextPlugins#GROOVY_CONSOLE}.
     *
     * @param context the Sling/AEM mock context (e.g. an {@code AemContext})
     * @param script  the Groovy script source to run
     * @return the run response
     */
    public static RunScriptResponse runScript(SlingContextImpl context, String script) {
        GroovyConsoleService service = context.getService(GroovyConsoleService.class);

        if (service == null) {
            throw new IllegalStateException("GroovyConsoleService is not registered. Add ContextPlugins.GROOVY_CONSOLE "
                    + "to your AemContextBuilder (and use ResourceResolverType.JCR_MOCK for scripts that touch the JCR).");
        }

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        RequestScriptContext scriptContext = new RequestScriptContext(
                context.request(),
                context.response(),
                outputStream,
                printStream(outputStream),
                script);

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
