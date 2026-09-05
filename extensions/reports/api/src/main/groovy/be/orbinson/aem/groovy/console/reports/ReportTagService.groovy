package be.orbinson.aem.groovy.console.reports

import org.apache.sling.api.resource.ResourceResolver
import org.osgi.annotation.versioning.ProviderType

/**
 * Browses AEM tags for the reports {@code TAG} parameter picker.  Backed by the AEM {@code TagManager}, so it
 * respects AEM tag semantics that a raw JCR read cannot: moved/merged tags (which linger as {@code cq:movedTo}
 * redirect nodes under {@code /content/cq:tags}) are filtered out, and titles/IDs reflect AEM's tag resolution.
 *
 * <p>Only available on AEM: the implementation is gated on the AEM tagging service, so on a plain Sling instance
 * no implementation is registered and the tag picker is simply empty.  The returned entries are plain maps
 * (no AEM types) so this API remains loadable everywhere.</p>
 */
@ProviderType
interface ReportTagService {

    /**
     * List the child tags of a tag, or the tag namespaces at the taxonomy root.
     *
     * @param resourceResolver resolver of the requesting user; tag visibility follows their JCR read access
     * @param path JCR path of the parent tag (e.g. {@code /content/cq:tags/marketing/regions}); when null,
     *        blank or the tags root ({@code /content/cq:tags}) the tag namespaces are returned
     * @return one entry per non-redirect child tag, each a map with keys {@code id} (tag ID), {@code path}
     *         (JCR path), {@code name} (node name), {@code title} (display title) and {@code hasChildren}
     */
    List<Map<String, Object>> listChildTags(ResourceResolver resourceResolver, String path)
}
