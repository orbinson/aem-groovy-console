package be.orbinson.aem.groovy.console.api

import groovy.json.JsonBuilder
import org.apache.sling.api.SlingHttpServletRequest
import org.apache.sling.api.SlingHttpServletResponse
import org.apache.sling.api.servlets.SlingSafeMethodsServlet
import org.osgi.framework.BundleContext

import javax.servlet.ServletException

/**
 * Base for extension page servlets that serve a standalone console-styled SPA shell (reports,
 * migrations): renders the HTML host document with the injected <code>window.__GC_CONFIG__</code>
 * and detects AEM by service name so extension bundles need no (optional) AEM API imports.
 *
 * Subclasses declare the servlet component and forward their <code>@Activate</code> method to
 * {@link #activate(BundleContext)}.
 */
abstract class AbstractConsoleUiPageServlet extends SlingSafeMethodsServlet {

    private volatile BundleContext bundleContext

    // public: Groovy dispatches the subclass's super.activate(...) call through the metaclass,
    // which does not see protected methods of a superclass from another bundle
    void activate(BundleContext bundleContext) {
        this.bundleContext = bundleContext
    }

    /** Page title of the HTML document. */
    protected abstract String getPageTitle()

    /** JCR path the SPA assets are served from, without trailing slash. */
    protected abstract String getAssetsPath()

    /** Root custom element of the SPA (e.g. <code>gcr-app</code>). */
    protected abstract String getAppElement()

    /** Base name of the SPA's stylesheet/script assets (e.g. <code>reports</code> for reports.css/reports.js). */
    protected abstract String getAssetName()

    /** Detect AEM by service name so the bundle needs no (optional) import of AEM API packages. */
    protected boolean isAem() {
        bundleContext?.getServiceReference("com.day.cq.wcm.api.PageManagerFactory") != null
    }

    /**
     * Cache-busting token for the SPA's stable-named entry assets.  The bundle's last-modified time changes on
     * every (re)deploy but is constant within one, so browsers refetch the entry after a deploy yet still cache it
     * during normal use.  The entry then pulls in its content-hashed chunks, so a redeploy needs no manual reload.
     */
    protected String getAssetVersion() {
        def bundle = bundleContext?.bundle

        bundle ? Long.toString(bundle.lastModified) : "0"
    }

    @Override
    protected void doGet(SlingHttpServletRequest request, SlingHttpServletResponse response)
            throws ServletException, IOException {
        def contextPath = request.contextPath ?: ""
        def configJson = new JsonBuilder([contextPath: contextPath, aem: isAem()]).toString()
        def version = assetVersion

        response.contentType = "text/html"
        response.characterEncoding = "UTF-8"

        response.writer.write("""\
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8"/>
    <meta name="viewport" content="width=device-width, initial-scale=1"/>
    <title>${pageTitle}</title>
    <link rel="stylesheet" href="${contextPath}${assetsPath}/${assetName}.css?v=${version}"/>
</head>
<body>
<${appElement}></${appElement}>
<script>window.__GC_CONFIG__ = ${configJson};</script>
<script type="module" src="${contextPath}${assetsPath}/${assetName}.js?v=${version}"></script>
</body>
</html>
""")
    }
}
