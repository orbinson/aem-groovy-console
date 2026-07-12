package be.orbinson.aem.groovy.console.reports.impl

import be.orbinson.aem.groovy.console.reports.ReportTagService
import com.day.cq.tagging.JcrTagManagerFactory
import com.day.cq.tagging.Tag
import groovy.util.logging.Slf4j
import org.apache.sling.api.resource.ResourceResolver
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

/**
 * AEM implementation of {@link ReportTagService} backed by the {@code TagManager}.  The mandatory reference to
 * {@link JcrTagManagerFactory} — an AEM-only OSGi service — gates the component: it is absent on plain Sling, so
 * this component never activates there and no {@code ReportTagService} is registered (the tag picker then returns
 * empty). {@code com.day.cq.*} imports are marked optional in the bundle so it still resolves on Sling.
 *
 * <p>Going through the {@code TagManager} (rather than reading {@code cq:Tag} nodes directly) means AEM's tag
 * semantics apply for free: {@code listChildren()} already hides moved/merged tags (the {@code cq:movedTo}
 * redirect stubs) and resolves display titles, so the picker never offers a dead tag.</p>
 */
@Component(service = ReportTagService)
@Slf4j("LOG")
class AemReportTagService implements ReportTagService {

    private static final String TAGS_ROOT = "/content/cq:tags"

    @Reference
    private JcrTagManagerFactory tagManagerFactory

    @Override
    List<Map<String, Object>> listChildTags(ResourceResolver resourceResolver, String path) {
        def tagManager = tagManagerFactory.getTagManager(resourceResolver)

        if (!tagManager) {
            return []
        }

        def normalized = path ?: TAGS_ROOT

        Iterator<Tag> children

        if (normalized == TAGS_ROOT) {
            children = Arrays.asList(tagManager.namespaces).iterator()
        } else {
            def tag = resourceResolver.getResource(normalized)?.adaptTo(Tag)

            if (!tag) {
                return []
            }

            children = tag.listChildren()
        }

        def tags = []

        while (children.hasNext()) {
            def tag = children.next()

            tags << [
                    id         : tag.tagID,
                    path       : tag.path,
                    name       : tag.name,
                    title      : tag.title ?: tag.name,
                    hasChildren: tag.listChildren().hasNext()
            ]
        }

        tags.sort { (it.title as String).toLowerCase() }
    }
}
