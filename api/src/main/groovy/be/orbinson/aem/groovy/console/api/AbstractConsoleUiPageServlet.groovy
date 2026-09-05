package be.orbinson.aem.groovy.console.api

import groovy.json.JsonBuilder
import groovy.util.logging.Slf4j
import org.apache.sling.api.SlingHttpServletRequest
import org.apache.sling.api.SlingHttpServletResponse
import org.apache.sling.api.resource.ResourceResolver
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
@Slf4j("LOG")
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

    @Override
    protected void doGet(SlingHttpServletRequest request, SlingHttpServletResponse response)
            throws ServletException, IOException {
        def contextPath = request.contextPath ?: ""
        def configJson = new JsonBuilder([contextPath: contextPath, aem: isAem()]).toString()
        def assets = resolveEntryAssets(request.resourceResolver)

        def cssLinks = assets.css
                .collect { href -> "    <link rel=\"stylesheet\" href=\"${contextPath}${href}\"/>" }
                .join("\n")

        response.contentType = "text/html"
        response.characterEncoding = "UTF-8"

        response.writer.write("""\
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8"/>
    <meta name="viewport" content="width=device-width, initial-scale=1"/>
    <title>${pageTitle}</title>
${cssLinks}
</head>
<body>
<${appElement}></${appElement}>
<script>window.__GC_CONFIG__ = ${configJson};</script>
<script type="module" src="${contextPath}${assets.js}"></script>
</body>
</html>
""")
    }

    /**
     * Resolve the SPA entry's JavaScript and stylesheet paths.  Prefers the content-hashed files listed in the
     * Vite build manifest (so an unchanged asset keeps its URL and stays cached, while a changed one gets a fresh
     * URL and is refetched — cache-busting without a query token, which would double-load the entry module).
     * Falls back to the stable <code>&lt;assetName&gt;.js/.css</code> names when no manifest is present.
     */
    private Map<String, Object> resolveEntryAssets(ResourceResolver resourceResolver) {
        def spaBase = assetsPath.endsWith("/assets") ? assetsPath[0..<(assetsPath.length() - "/assets".length())]
                : assetsPath
        def entry = SpaManifest.entry(resourceResolver, spaBase, assetName)

        if (entry.js) {
            return entry
        }

        [js: "${assetsPath}/${assetName}.js".toString(),
         css: ["${assetsPath}/${assetName}.css".toString()]] as Map<String, Object>
    }
}
