package be.orbinson.aem.groovy.console.extension.impl

import be.orbinson.aem.groovy.console.api.*
import be.orbinson.aem.groovy.console.api.context.ScriptContext
import be.orbinson.aem.groovy.console.api.impl.RequestScriptContext
import be.orbinson.aem.groovy.console.extension.ExtensionService
import com.google.common.io.ByteStreams
import io.wcm.testing.mock.aem.junit5.AemContext
import io.wcm.testing.mock.aem.junit5.AemContextExtension
import org.apache.sling.testing.mock.sling.ResourceResolverType
import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.MultipleCompilationErrorsException
import org.codehaus.groovy.control.customizers.CompilationCustomizer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

import java.text.SimpleDateFormat

import static org.junit.jupiter.api.Assertions.assertEquals
import static org.junit.jupiter.api.Assertions.assertThrows

@ExtendWith(AemContextExtension.class)
class DefaultExtensionServiceTest {

    private final AemContext context = new AemContext(ResourceResolverType.JCR_MOCK);

    static final def SELECTORS = "mobile"

    static final def PARAMETERS = [firstName: "Clarence", lastName: "Wiggum"]

    static final def SCRIPT = "new SimpleDateFormat()"

    class TestStarImportExtensionProvider implements StarImportExtensionProvider {

        @Override
        Set<StarImport> getStarImports() {
            [new StarImport(SimpleDateFormat.package.name)] as Set
        }
    }

    class FirstBindingExtensionProvider implements BindingExtensionProvider {

        @Override
        Map<String, BindingVariable> getBindingVariables(ScriptContext scriptContext) {
            [
                    parameterNames: new BindingVariable((scriptContext as RequestScriptContext).request.parameterMap.keySet()),
                    selectors     : new BindingVariable([])
            ]
        }
    }

    class SecondBindingExtensionProvider implements BindingExtensionProvider {

        @Override
        Map<String, BindingVariable> getBindingVariables(ScriptContext scriptContext) {
            [
                    path     : new BindingVariable((scriptContext as RequestScriptContext).request.requestPathInfo.resourcePath),
                    selectors: new BindingVariable((scriptContext as RequestScriptContext).request.requestPathInfo.selectors as List)
            ]
        }
    }

    class TestScriptMetaClassExtensionProvider implements ScriptMetaClassExtensionProvider {

        @Override
        Closure getScriptMetaClass(ScriptContext scriptContext) {
            def closure = {

            }

            closure
        }
    }

    @Test
    void getCompilationCustomizers() {
        def extensionService = new DefaultExtensionService()
        def firstProvider = new TestStarImportExtensionProvider()

        extensionService.bindStarImportExtensionProvider(firstProvider)

        assertEquals(extensionService.compilationCustomizers.size(), 1)

        extensionService.unbindStarImportExtensionProvider(firstProvider)

        assertEquals(extensionService.compilationCustomizers.size(), 0)
    }

    @Test
    void starImports() {
        def extensionService = new DefaultExtensionService()
        def starImportExtensionProvider = new TestStarImportExtensionProvider()

        extensionService.bindStarImportExtensionProvider(starImportExtensionProvider)

        runScriptWithExtensionService(extensionService)

        extensionService.unbindStarImportExtensionProvider(starImportExtensionProvider)


        assertThrows(MultipleCompilationErrorsException) {
            runScriptWithExtensionService(extensionService)
        }
    }

    @Test
    void getBinding() {
        def request = context.request();
        request.setParameterMap(PARAMETERS);
        request.getRequestPathInfo().setSelectorString(SELECTORS)
        request.getRequestPathInfo().setResourcePath("/")

        def response = context.response()

        def scriptContext = new RequestScriptContext(request, response, null, null, null)

        def extensionService = new DefaultExtensionService()
        def firstProvider = new FirstBindingExtensionProvider()
        def secondProvider = new SecondBindingExtensionProvider()

        extensionService.bindBindingExtensionProvider(firstProvider)
        extensionService.bindBindingExtensionProvider(secondProvider)

        assert extensionService.getBindingVariables(scriptContext)["selectors"].value == request.requestPathInfo.selectors as List
        assert extensionService.getBindingVariables(scriptContext)["parameterNames"].value == request.parameterMap.keySet()
        assert extensionService.getBindingVariables(scriptContext)["path"].value == "/"

        extensionService.unbindBindingExtensionProvider(secondProvider)

        assert extensionService.getBindingVariables(scriptContext)["selectors"].value == []

        assert !extensionService.getBindingVariables(scriptContext)["path"]
    }

    @Test
    void getScriptMetaClasses() {
        def request = context.request();

        def scriptContext = new RequestScriptContext(request)

        def extensionService = new DefaultExtensionService()
        def firstProvider = new TestScriptMetaClassExtensionProvider()
        def secondProvider = new TestScriptMetaClassExtensionProvider()

        extensionService.bindScriptMetaClassExtensionProvider(firstProvider)
        extensionService.bindScriptMetaClassExtensionProvider(secondProvider)

        assert extensionService.getScriptMetaClasses(scriptContext).size() == 2

        extensionService.unbindScriptMetaClassExtensionProvider(secondProvider)

        assert extensionService.getScriptMetaClasses(scriptContext).size() == 1
    }

    private void runScriptWithExtensionService(ExtensionService extensionService) {
        def binding = new Binding(out: new PrintStream(ByteStreams.nullOutputStream()))

        def configuration = new CompilerConfiguration().addCompilationCustomizers(
                extensionService.compilationCustomizers as CompilationCustomizer[])

        new GroovyShell(binding, configuration).parse(SCRIPT).run()
    }
}
