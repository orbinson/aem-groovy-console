package be.orbinson.aem.groovy.console.extension.impl

import be.orbinson.aem.groovy.console.api.*
import be.orbinson.aem.groovy.console.api.context.ScriptContext
import be.orbinson.aem.groovy.console.extension.ExtensionService
import be.orbinson.aem.groovy.console.extension.MetaClassExtensionProvider
import groovy.transform.Synchronized
import groovy.util.logging.Slf4j
import org.codehaus.groovy.control.customizers.CompilationCustomizer
import org.codehaus.groovy.control.customizers.ImportCustomizer
import org.codehaus.groovy.runtime.InvokerHelper
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ReferenceCardinality
import org.osgi.service.component.annotations.ReferencePolicy

import java.util.concurrent.CopyOnWriteArrayList

@Component(service = ExtensionService, immediate = true)
@Slf4j("LOG")
class DefaultExtensionService implements ExtensionService {

    private volatile List<MetaClassExtensionProvider> metaClassExtensionProviders = new CopyOnWriteArrayList<>()

    private volatile List<BindingExtensionProvider> bindingExtensionProviders = new CopyOnWriteArrayList<>()

    private volatile List<StarImportExtensionProvider> starImportExtensionProviders = new CopyOnWriteArrayList<>()

    private volatile List<ScriptMetaClassExtensionProvider> scriptMetaClassExtensionProviders = new CopyOnWriteArrayList<>()

    private volatile List<CompilationCustomizerExtensionProvider> compilationCustomizerExtensionProviders =
            new CopyOnWriteArrayList<>()

    @Override
    Map<String, BindingVariable> getBindingVariables(ScriptContext scriptContext) {
        def bindingVariables = [:] as Map<String, BindingVariable>

        bindingExtensionProviders.each { extension ->
            extension.getBindingVariables(scriptContext).each { name, variable ->
                if (bindingVariables[name]) {
                    LOG.debug("binding variable {} is currently bound to value {}, overriding with value = {}", name,
                            bindingVariables[name], variable.value)
                }

                bindingVariables[name] = variable
            }
        }

        bindingVariables
    }

    @Override
    Set<Class> getMetaClasses() {
        def metaClasses = [] as LinkedHashSet

        metaClassExtensionProviders.each { provider ->
            metaClasses.addAll(provider.metaClasses.keySet())
        }

        metaClasses
    }

    @Reference(cardinality = ReferenceCardinality.MULTIPLE, policy = ReferencePolicy.DYNAMIC)
    @Synchronized
    void bindMetaClassExtensionProvider(MetaClassExtensionProvider extension) {
        metaClassExtensionProviders.add(extension)

        LOG.info("added metaclass extension provider = {}", extension.class.name)

        extension.metaClasses.each { clazz, metaClassClosure ->
            clazz.metaClass(metaClassClosure)

            LOG.info("added metaclass for class = {}", clazz.name)
        }
    }

    @Synchronized
    void unbindMetaClassExtensionProvider(MetaClassExtensionProvider extension) {
        metaClassExtensionProviders.remove(extension)

        LOG.info("removed metaclass extension provider = {}", extension.class.name)

        // remove metaclass from registry for each mapped class
        extension.metaClasses.each { clazz, closure ->
            InvokerHelper.metaRegistry.removeMetaClass(clazz)

            LOG.info("removed metaclass for class = {}", clazz.name)

            // ensure that valid metaclasses are still registered
            metaClassExtensionProviders.each {
                def metaClassClosure = it.metaClasses[clazz]

                if (metaClassClosure) {
                    LOG.info("retaining metaclass for class = {} from service = {}", clazz.name, it.class.name)

                    clazz.metaClass(metaClassClosure)
                }
            }
        }
    }

    @Override
    List<Closure> getScriptMetaClasses(ScriptContext scriptContext) {
        scriptMetaClassExtensionProviders*.getScriptMetaClass(scriptContext)
    }

    @Override
    Set<StarImport> getStarImports() {
        starImportExtensionProviders.collectMany { it.starImports } as Set<StarImport>
    }

    @Override
    List<CompilationCustomizer> getCompilationCustomizers() {
        def importPackageNames = starImports.collect { it.packageName }

        def compilationCustomizers = []

        if (importPackageNames) {
            compilationCustomizers.add(new ImportCustomizer().addStarImports(importPackageNames as String[]))
        }

        compilationCustomizerExtensionProviders.each { provider ->
            compilationCustomizers.addAll(provider.compilationCustomizers)
        }

        compilationCustomizers
    }

    @Reference(cardinality = ReferenceCardinality.MULTIPLE, policy = ReferencePolicy.DYNAMIC)
    @Synchronized
    void bindBindingExtensionProvider(BindingExtensionProvider extension) {
        bindingExtensionProviders.add(extension)

        LOG.info("added binding extension : {}", extension.class.name)
    }

    @Synchronized
    void unbindBindingExtensionProvider(BindingExtensionProvider extension) {
        bindingExtensionProviders.remove(extension)

        LOG.info("removed binding extension : {}", extension.class.name)
    }

    @Reference(cardinality = ReferenceCardinality.MULTIPLE, policy = ReferencePolicy.DYNAMIC)
    @Synchronized
    void bindStarImportExtensionProvider(StarImportExtensionProvider extension) {
        starImportExtensionProviders.add(extension)

        LOG.info("added star import extension : {} with imports : {}", extension.class.name, extension.starImports)
    }

    @Synchronized
    void unbindStarImportExtensionProvider(StarImportExtensionProvider extension) {
        starImportExtensionProviders.remove(extension)

        LOG.info("removed star import extension : {} with imports : {}", extension.class.name, extension.starImports)
    }

    @Reference(cardinality = ReferenceCardinality.MULTIPLE, policy = ReferencePolicy.DYNAMIC)
    @Synchronized
    void bindScriptMetaClassExtensionProvider(ScriptMetaClassExtensionProvider extension) {
        scriptMetaClassExtensionProviders.add(extension)

        LOG.info("added script metaclass extension : {}", extension.class.name)
    }

    @Synchronized
    void unbindScriptMetaClassExtensionProvider(ScriptMetaClassExtensionProvider extension) {
        scriptMetaClassExtensionProviders.remove(extension)

        LOG.info("removed script metaclass extension : {}", extension.class.name)
    }

    @Reference(cardinality = ReferenceCardinality.MULTIPLE, policy = ReferencePolicy.DYNAMIC)
    @Synchronized
    void bindCompilationCustomizerExtensionProvider(CompilationCustomizerExtensionProvider extension) {
        compilationCustomizerExtensionProviders.add(extension)

        LOG.info("adding compilation customizer extension : {}", extension.class.name)
    }

    @Synchronized
    void unbindCompilationCustomizerExtensionProvider(CompilationCustomizerExtensionProvider extension) {
        compilationCustomizerExtensionProviders.remove(extension)

        LOG.info("removed compilation customizer extension : {}", extension.class.name)
    }
}
