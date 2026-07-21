package be.orbinson.aem.groovy.console.reports.servlets

import be.orbinson.aem.groovy.console.reports.ReportTagService
import groovy.util.logging.Slf4j
import org.apache.jackrabbit.JcrConstants
import org.apache.sling.api.SlingHttpServletRequest
import org.apache.sling.api.SlingHttpServletResponse
import org.apache.sling.api.resource.Resource
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ReferenceCardinality
import org.osgi.service.component.annotations.ReferencePolicy

import javax.servlet.Servlet
import javax.servlet.ServletException

/**
 * Repository browser backing the reports UI path/tag picker.
 *
 * <code>GET /bin/groovyconsole/reports/browse.json?path=&amp;type=</code>
 *
 * Lists the children of <code>path</code> using the <em>requesting user's</em> resource resolver, so JCR
 * read ACLs naturally limit what can be browsed.  <code>type</code> selects what is shown/selectable:
 * <ul>
 *     <li><code>NODE</code> — any JCR node (default)</li>
 *     <li><code>PAGE</code> — AEM pages ({@code cq:Page}); folders are shown for navigation</li>
 *     <li><code>ASSET</code> — DAM assets ({@code dam:Asset}); folders are shown for navigation</li>
 *     <li><code>TAG</code> — AEM tags, delegated to the AEM-only {@link ReportTagService} (backed by the
 *         {@code TagManager}, so moved/merged redirect tags are hidden). Each entry carries its tag
 *         <code>id</code>. On plain Sling no {@code ReportTagService} is registered, so the tag tree is empty.</li>
 * </ul>
 */
@Component(service = Servlet, immediate = true, property = [
        "sling.servlet.paths=/bin/groovyconsole/reports/browse"
])
@Slf4j("LOG")
class ReportBrowseServlet extends AbstractReportsServlet {

    private static final int MAX_CHILDREN = 500

    // how many children to peek when deciding whether a node shows an expand twisty (bounds the probe cost)
    private static final int CHILD_PROBE_LIMIT = 20

    private static final List<String> FOLDER_TYPES = [
            "sling:Folder", "sling:OrderedFolder", "nt:folder", "nt:unstructured"
    ]

    // path-browser filter types
    private static final String TYPE_PAGE = "PAGE"

    private static final String TYPE_ASSET = "ASSET"

    private static final String TYPE_TAG = "TAG"

    private static final String TAGS_ROOT = "/content/cq:tags"

    // AEM primary node types matched per filter (matched by string so the PATH/PAGE/ASSET browse imports no AEM
    // API and stays resolvable on plain Sling — where these nodes simply do not exist and browsing returns empty)
    private static final String PRIMARY_TYPE_PAGE = "cq:Page"

    private static final String PRIMARY_TYPE_ASSET = "dam:Asset"

    // AEM-only; absent on Sling, where the TAG picker then returns empty
    @Reference(cardinality = ReferenceCardinality.OPTIONAL, policy = ReferencePolicy.DYNAMIC)
    private volatile ReportTagService tagService

    @Override
    protected void doGet(SlingHttpServletRequest request, SlingHttpServletResponse response)
            throws ServletException, IOException {
        def resolver = request.resourceResolver

        if (resolver.userID == "anonymous") {
            writeError(response, javax.servlet.http.HttpServletResponse.SC_FORBIDDEN, "Authentication required")

            return
        }

        def type = (request.getParameter("type") ?: "NODE").toUpperCase()
        def path = request.getParameter("path") ?: "/"

        if (type == TYPE_TAG) {
            browseTags(response, resolver, path)

            return
        }

        def resource = resolver.getResource(path)

        if (!resource) {
            writeJsonResponse(response, [path: path, children: []])

            return
        }

        def children = []
        def truncated = false

        def iterator = resource.listChildren()

        while (iterator.hasNext()) {
            def child = iterator.next()

            if (!shown(child, type)) {
                continue
            }

            if (children.size() >= MAX_CHILDREN) {
                truncated = true

                break
            }

            children << [
                    name        : child.name,
                    path        : child.path,
                    title       : title(child),
                    primaryType : primaryType(child),
                    hasChildren : hasChildNodes(child, type),
                    selectable  : selectable(child, type)
            ]
        }

        children.sort { a, b -> (a.name as String) <=> (b.name as String) }

        writeJsonResponse(response, [path: path, children: children, truncated: truncated])
    }

    // internals

    // Tag browsing is delegated to the AEM-only ReportTagService (TagManager-backed). Absent on Sling -> empty.
    private void browseTags(SlingHttpServletResponse response, resolver, String path) {
        def service = tagService

        if (!service) {
            writeJsonResponse(response, [path: path, children: []])

            return
        }

        // "/" is the picker's generic default; for tags the taxonomy root means "list the namespaces"
        def tagPath = (!path || path == "/") ? TAGS_ROOT : path

        def children = service.listChildTags(resolver, tagPath).collect { tag ->
            [
                    name       : tag.name,
                    path       : tag.path,
                    title      : tag.title,
                    id         : tag.id,
                    hasChildren: tag.hasChildren,
                    selectable : true
            ]
        }

        writeJsonResponse(response, [path: path, children: children])
    }

    private static String primaryType(Resource resource) {
        resource.valueMap.get(JcrConstants.JCR_PRIMARYTYPE, String)
    }

    private static boolean isHidden(Resource resource) {
        def name = resource.name

        name == JcrConstants.JCR_CONTENT || name.startsWith("rep:")
    }

    private static boolean isFolder(String primaryType) {
        primaryType in FOLDER_TYPES
    }

    private static boolean shown(Resource resource, String type) {
        if (isHidden(resource)) {
            return false
        }

        def primaryType = primaryType(resource)

        switch (type) {
            case TYPE_PAGE:
                return primaryType == PRIMARY_TYPE_PAGE || isFolder(primaryType)
            case TYPE_ASSET:
                return primaryType == PRIMARY_TYPE_ASSET || isFolder(primaryType)
            default:
                return true
        }
    }

    private static boolean selectable(Resource resource, String type) {
        def primaryType = primaryType(resource)

        switch (type) {
            case TYPE_PAGE:
                return primaryType == PRIMARY_TYPE_PAGE
            case TYPE_ASSET:
                return primaryType == PRIMARY_TYPE_ASSET
            default:
                return true
        }
    }

    /**
     * Cheap "is this node expandable" probe for the tree's twisty.  We deliberately do NOT scan a node's
     * full child list nor apply the type filter to grandchildren here — that would make a single expand
     * O(children x grandchildren) on large trees (e.g. /content/dam).  The actual (type-filtered) children
     * are loaded lazily when the user opens the node; here we only peek for any non-hidden child, bounded so
     * even a pathological node is never fully traversed.
     */
    private static boolean hasChildNodes(Resource resource, String type) {
        // assets are leaves in the asset picker — never expandable
        if (type == TYPE_ASSET && primaryType(resource) == PRIMARY_TYPE_ASSET) {
            return false
        }

        def iterator = resource.listChildren()
        def probed = 0

        while (iterator.hasNext() && probed < CHILD_PROBE_LIMIT) {
            if (!isHidden(iterator.next())) {
                return true
            }

            probed++
        }

        false
    }

    /**
     * Display title following Granite's pathbrowser precedence: cq:Page / folders expose jcr:content/jcr:title,
     * dam:Asset exposes jcr:content/metadata/dc:title, plain folders may carry jcr:title directly. Returns null
     * when none is set so the client falls back to the node name.
     */
    private static String title(Resource resource) {
        def content = resource.getChild(JcrConstants.JCR_CONTENT)

        def title = content?.valueMap?.get("jcr:title", String)

        if (!title) {
            def metadata = content?.getChild("metadata")
            title = firstString(metadata?.valueMap?.get("dc:title"))
        }

        if (!title) {
            title = resource.valueMap.get("jcr:title", String)
        }

        title ?: null
    }

    /** dc:title is sometimes a multi-value property; take the first non-blank entry. */
    private static String firstString(Object value) {
        if (value instanceof String[]) {
            return value.find { it } as String
        }

        value as String ?: null
    }
}
