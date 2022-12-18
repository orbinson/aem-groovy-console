package be.orbinson.aem.groovy.console.impl

import be.orbinson.aem.groovy.console.GroovyConsoleService
import be.orbinson.aem.groovy.console.api.impl.RequestScriptContext
import be.orbinson.aem.groovy.console.api.impl.RequestScriptData
import be.orbinson.aem.groovy.console.audit.AuditService
import be.orbinson.aem.groovy.console.configuration.ConfigurationService
import be.orbinson.aem.groovy.console.constants.GroovyConsoleConstants
import be.orbinson.aem.groovy.console.extension.impl.DefaultBindingExtensionProvider
import be.orbinson.aem.groovy.console.extension.impl.DefaultExtensionService
import be.orbinson.aem.groovy.console.extension.impl.DefaultScriptMetaClassExtensionProvider
import com.day.cq.commons.jcr.JcrConstants
import com.day.cq.replication.Replicator
import com.day.cq.search.QueryBuilder
import com.google.common.base.Charsets
import io.wcm.testing.mock.aem.junit5.AemContext
import io.wcm.testing.mock.aem.junit5.AemContextExtension
import org.apache.sling.event.jobs.JobManager
import org.apache.sling.testing.mock.sling.ResourceResolverType
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

import javax.jcr.Session

import static be.orbinson.aem.groovy.console.constants.GroovyConsoleConstants.PATH_SCRIPTS_FOLDER
import static be.orbinson.aem.groovy.console.constants.GroovyConsoleConstants.SCRIPT
import static org.junit.jupiter.api.Assertions.assertNotNull
import static org.junit.jupiter.api.Assertions.assertTrue
import static org.mockito.Mockito.mock

@ExtendWith(AemContextExtension.class)
class DefaultGroovyConsoleServiceTest {

    private final AemContext context = new AemContext(ResourceResolverType.JCR_MOCK);

    def SCRIPT_NAME = "Script"

    def SCRIPT_FILE_NAME = "${SCRIPT_NAME}.groovy"

    def PATH_FILE = "$PATH_SCRIPTS_FOLDER/$SCRIPT_FILE_NAME"

    def PATH_FILE_CONTENT = "$PATH_FILE/${JcrConstants.JCR_CONTENT}"

    @BeforeEach
    void beforeEach() {
        context.registerService(JobManager, mock(JobManager))
        context.registerService(QueryBuilder, mock(QueryBuilder))
        context.registerService(ConfigurationService, mock(ConfigurationService))
        context.registerService(AuditService, mock(AuditService))
        context.registerService(Replicator, mock(Replicator))
        context.registerInjectActivateService(new DefaultBindingExtensionProvider())
        context.registerInjectActivateService(new DefaultExtensionService())
        context.registerInjectActivateService(new DefaultScriptMetaClassExtensionProvider())
        context.registerInjectActivateService(new DefaultGroovyConsoleService())
    }

    @Test
    void runScript() {
        def consoleService = context.getService(GroovyConsoleService)

        def request = context.request()
        def response = context.response();

        def outputStream = new ByteArrayOutputStream()

        def scriptContext = new RequestScriptContext(
                request: request,
                response: response,
                outputStream: outputStream,
                printStream: new PrintStream(outputStream, true, Charsets.UTF_8.name()),
                script: scriptAsString
        )

        def map = consoleService.runScript(scriptContext)
        assertScriptResult(map)
    }

    @Test
    void saveScript() {

        def consoleService = context.getService(GroovyConsoleService)

        def request = context.request();
        request.setParameterMap(this.parameterMap)

        def scriptData = new RequestScriptData(request)

        consoleService.saveScript(scriptData)

        assertNodeExists(PATH_SCRIPTS_FOLDER, JcrConstants.NT_FOLDER)
        assertNodeExists(PATH_FILE, JcrConstants.NT_FILE)
        assertNodeExists(PATH_FILE_CONTENT, JcrConstants.NT_RESOURCE)

        assertNotNull(context.resourceResolver().adaptTo(Session).getNode(PATH_FILE_CONTENT).getProperty(JcrConstants.JCR_DATA).getBinary().getStream().getText())
    }

    void assertScriptResult(map) {
        assert !map.result
        assert map.output == "BEER" + System.getProperty("line.separator")
        assert !map.exceptionStackTrace
        assert map.runningTime
    }

    private String getScriptAsString() {
        def scriptAsString = null

        this.class.getResourceAsStream("/$SCRIPT_FILE_NAME").withStream { stream ->
            scriptAsString = stream.text
        }

        scriptAsString
    }

    private Map<String, Object> getParameterMap() {
        [(GroovyConsoleConstants.FILE_NAME): (SCRIPT_NAME), (SCRIPT): scriptAsString]
    }

    void assertNodeExists(String path, String type) {
        def node = context.resourceResolver().getResource(path);
        assertNotNull(node)
        assertTrue(node.isResourceType(type))
    }
}
