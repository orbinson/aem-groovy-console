package be.orbinson.aem.groovy.console.impl

import be.orbinson.aem.groovy.console.GroovyConsoleService
import be.orbinson.aem.groovy.console.api.context.impl.RequestScriptContext
import be.orbinson.aem.groovy.console.api.context.impl.RequestScriptData
import be.orbinson.aem.groovy.console.audit.AuditService
import be.orbinson.aem.groovy.console.configuration.ConfigurationService
import be.orbinson.aem.groovy.console.constants.GroovyConsoleConstants
import be.orbinson.aem.groovy.console.extension.impl.DefaultExtensionService
import be.orbinson.aem.groovy.console.extension.impl.binding.SlingBindingExtensionProvider
import be.orbinson.aem.groovy.console.extension.impl.scriptmetaclass.SlingScriptMetaClassExtensionProvider
import com.day.cq.replication.Replicator
import com.day.cq.search.QueryBuilder
import io.wcm.testing.mock.aem.junit5.AemContext
import io.wcm.testing.mock.aem.junit5.AemContextExtension
import org.apache.commons.io.IOUtils
import org.apache.jackrabbit.JcrConstants
import org.apache.sling.event.jobs.JobManager
import org.apache.sling.jcr.resource.api.JcrResourceConstants
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

import java.nio.charset.StandardCharsets

import static be.orbinson.aem.groovy.console.constants.GroovyConsoleConstants.PATH_SCRIPTS_FOLDER
import static be.orbinson.aem.groovy.console.constants.GroovyConsoleConstants.SCRIPT
import static org.junit.jupiter.api.Assertions.*
import static org.mockito.Mockito.mock

@ExtendWith(AemContextExtension.class)
class DefaultGroovyConsoleServiceTest {

    private final AemContext context = new AemContext();

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
        context.registerInjectActivateService(new SlingBindingExtensionProvider())
        context.registerInjectActivateService(new DefaultExtensionService())
        context.registerInjectActivateService(new SlingScriptMetaClassExtensionProvider())
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
                printStream: new PrintStream(outputStream, true, StandardCharsets.UTF_8.name()),
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

        assertResourceExists(PATH_SCRIPTS_FOLDER, JcrResourceConstants.NT_SLING_FOLDER)
        assertResourceExists(PATH_FILE, JcrConstants.NT_FILE)
        assertResourceExists(PATH_FILE_CONTENT, JcrConstants.NT_RESOURCE)

        String script = IOUtils.toString(context.resourceResolver().getResource(PATH_FILE).adaptTo(InputStream.class), StandardCharsets.UTF_8.name());
        assertEquals("println \"BEER\"", script);
    }

    void assertScriptResult(map) {
        assertNull(map.result)
        assertEquals("BEER" + System.lineSeparator(), map.output)
        assertEquals("", map.exceptionStackTrace)
        assertNotNull(map.runningTime)
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

    void assertResourceExists(String path, String type) {
        def resource = context.resourceResolver().getResource(path);
        assertNotNull(resource)
        assertTrue(resource.isResourceType(type))
    }
}
