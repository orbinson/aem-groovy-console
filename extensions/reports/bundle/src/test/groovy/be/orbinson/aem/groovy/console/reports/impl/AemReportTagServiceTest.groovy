package be.orbinson.aem.groovy.console.reports.impl

import be.orbinson.aem.groovy.console.reports.ReportTagService
import com.day.cq.tagging.TagManager
import io.wcm.testing.mock.aem.junit5.AemContext
import io.wcm.testing.mock.aem.junit5.AemContextExtension
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

import static org.junit.jupiter.api.Assertions.assertEquals
import static org.junit.jupiter.api.Assertions.assertFalse
import static org.junit.jupiter.api.Assertions.assertTrue

@ExtendWith(AemContextExtension.class)
class AemReportTagServiceTest {

    private final AemContext context = new AemContext()

    private ReportTagService tagService

    @BeforeEach
    void beforeEach() {
        def tagManager = context.resourceResolver().adaptTo(TagManager)

        tagManager.createTag("colors:red", "Red", null)
        tagManager.createTag("colors:green", "Green", null)
        tagManager.createTag("colors:blue", "Blue", null)
        tagManager.createTag("fruits:apple", "Apple", null)

        context.resourceResolver().commit()

        tagService = context.registerInjectActivateService(new AemReportTagService())
    }

    private static List<String> ids(List<Map<String, Object>> tags) {
        tags.collect { it.id as String }
    }

    @Test
    void "lists tag namespaces at the taxonomy root"() {
        def namespaces = tagService.listChildTags(context.resourceResolver(), "/content/cq:tags")

        assertTrue(ids(namespaces).containsAll(["colors:", "fruits:"]),
                "expected both namespaces, got ${ids(namespaces)}")
        assertTrue(namespaces.find { it.id == "colors:" }.hasChildren as boolean, "colors namespace has children")
    }

    @Test
    void "lists the child tags of a namespace with id, title and path"() {
        def children = tagService.listChildTags(context.resourceResolver(), "/content/cq:tags/colors")

        assertEquals(["colors:blue", "colors:green", "colors:red"], ids(children).sort())

        def red = children.find { it.id == "colors:red" }
        assertEquals("Red", red.title)
        assertEquals("/content/cq:tags/colors/red", red.path)
        assertFalse(red.hasChildren as boolean)
    }

    @Test
    void "returns empty for a path that is not a tag"() {
        assertTrue(tagService.listChildTags(context.resourceResolver(), "/content/no/such/tag").isEmpty())
    }
}
