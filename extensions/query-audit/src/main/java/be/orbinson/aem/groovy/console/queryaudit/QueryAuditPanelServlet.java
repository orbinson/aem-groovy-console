package be.orbinson.aem.groovy.console.queryaudit;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import javax.servlet.Servlet;
import javax.servlet.http.HttpServletResponse;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;
import org.osgi.service.component.annotations.Component;

/**
 * Serves the query-audit panel ES module for the modern console UI. Keeping the module in the bundle (rather than a
 * content package) lets the query-audit extension stay a single bundle. The module is announced by
 * {@link QueryAuditConsoleUiExtensionProvider} and imported by the console SPA at startup.
 */
@Component(service = Servlet.class, immediate = true, property = {
        "sling.servlet.paths=/bin/groovyconsole/query-audit-panel.js"
})
public class QueryAuditPanelServlet extends SlingSafeMethodsServlet {

    private static final String MODULE_RESOURCE = "/query-audit-panel.js";

    @Override
    protected void doGet(SlingHttpServletRequest request, SlingHttpServletResponse response) throws IOException {
        response.setContentType("application/javascript");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        try (InputStream module = getClass().getResourceAsStream(MODULE_RESOURCE)) {
            if (module == null) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
            response.getWriter().write(new String(readAll(module), StandardCharsets.UTF_8));
        }
    }

    private static byte[] readAll(InputStream in) throws IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] chunk = new byte[4096];
        int read;
        while ((read = in.read(chunk)) != -1) {
            out.write(chunk, 0, read);
        }
        return out.toByteArray();
    }
}
