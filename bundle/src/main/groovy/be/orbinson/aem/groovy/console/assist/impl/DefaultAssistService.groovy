package be.orbinson.aem.groovy.console.assist.impl

import be.orbinson.aem.groovy.console.api.context.ScriptContext
import be.orbinson.aem.groovy.console.assist.AssistService
import be.orbinson.aem.groovy.console.impl.GroovyShellFactory
import groovy.util.logging.Slf4j
import org.codehaus.groovy.control.MultipleCompilationErrorsException
import org.codehaus.groovy.control.messages.SyntaxErrorMessage
import org.codehaus.groovy.runtime.InvokerHelper
import org.osgi.framework.Bundle
import org.osgi.framework.BundleContext
import org.osgi.framework.BundleEvent
import org.osgi.framework.BundleListener
import org.osgi.framework.wiring.BundleWiring
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Deactivate
import org.osgi.service.component.annotations.Reference

import java.lang.reflect.Method
import java.lang.reflect.Modifier

@Component(service = AssistService, immediate = true)
@Slf4j("LOG")
class DefaultAssistService implements AssistService, BundleListener {

    private static final int MEMBERS_CACHE_SIZE = 256
    private BundleContext bundleContext

    private volatile Map<String, Boolean> classIndex

    private final Map<String, Map<String, Object>> membersCache = Collections.synchronizedMap(
            new LinkedHashMap<String, Map<String, Object>>(MEMBERS_CACHE_SIZE, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Map<String, Object>> eldest) {
                    size() > MEMBERS_CACHE_SIZE
                }
            })

    @Reference
    private GroovyShellFactory groovyShellFactory

    @Activate
    void activate(BundleContext bundleContext) {
        this.bundleContext = bundleContext

        bundleContext.addBundleListener(this)
    }

    @Deactivate
    void deactivate() {
        bundleContext?.removeBundleListener(this)
    }

    @Override
    void bundleChanged(BundleEvent event) {
        if (event.type in [BundleEvent.STARTED, BundleEvent.STOPPED, BundleEvent.UNINSTALLED, BundleEvent.UPDATED]) {
            LOG.debug("invalidating class index after bundle event : {}", event.type)

            classIndex = null
            membersCache.clear()
        }
    }

    @Override
    Map<String, Object> findClasses(String prefix, int limit) {
        def index = getOrBuildClassIndex()
        def query = prefix ?: ""
        def queryLowerCase = query.toLowerCase()

        def matches = []
        def truncated = false

        for (entry in index.entrySet()) {
            def fqcn = entry.key
            def simpleName = fqcn.substring(fqcn.lastIndexOf('.') + 1)

            if (!query || fqcn.startsWith(query) || simpleName.toLowerCase().startsWith(queryLowerCase)) {
                if (matches.size() >= limit) {
                    truncated = true
                    break
                }

                matches << [
                        fqcn    : fqcn,
                        name    : simpleName,
                        package : fqcn.contains('.') ? fqcn.substring(0, fqcn.lastIndexOf('.')) : "",
                        exported: entry.value
                ]
            }
        }

        [
                truncated: truncated,
                classes  : matches
        ]
    }

    @Override
    Map<String, Object> getMembers(String className) {
        def cached = membersCache[className]

        if (cached != null) {
            return cached
        }

        Class clazz

        try {
            clazz = Class.forName(className, false, this.class.classLoader)
        } catch (Throwable t) {
            LOG.debug("class not loadable : {}, {}", className, t.toString())

            return [fqcn: className, members: [], error: "not loadable"]
        }

        def members = []
        def seen = [] as Set

        clazz.methods.each { Method method ->
            def key = "${method.name}(${method.parameterTypes*.name.join(',')})" as String

            if (seen.add(key) && !method.synthetic) {
                members << [
                        kind      : "method",
                        name      : method.name,
                        returnType: method.returnType.name,
                        params    : method.parameterTypes*.name,
                        static    : Modifier.isStatic(method.modifiers),
                        source    : "java"
                ]
            }
        }

        clazz.fields.each { field ->
            members << [
                    kind  : "field",
                    name  : field.name,
                    type  : field.type.name,
                    static: Modifier.isStatic(field.modifiers),
                    source: "java"
            ]
        }

        // bean properties derived from getter methods
        clazz.methods.each { Method method ->
            def name = method.name
            def propertyName = null

            if (name.startsWith("get") && name.length() > 3 && !method.parameterTypes && method.returnType != void) {
                propertyName = name[3].toLowerCase() + name.substring(4)
            } else if (name.startsWith("is") && name.length() > 2 && !method.parameterTypes
                    && method.returnType in [boolean, Boolean]) {
                propertyName = name[2].toLowerCase() + name.substring(3)
            }

            if (propertyName && seen.add("property:$propertyName" as String)) {
                members << [
                        kind  : "property",
                        name  : propertyName,
                        type  : method.returnType.name,
                        static: Modifier.isStatic(method.modifiers),
                        source: "java"
                ]
            }
        }

        // Groovy metaclass methods (DefaultGroovyMethods and registered metaclass extensions)
        try {
            InvokerHelper.getMetaClass(clazz).metaMethods.each { metaMethod ->
                def key = "${metaMethod.name}(${metaMethod.nativeParameterTypes*.name.join(',')})" as String

                if (seen.add(key)) {
                    members << [
                            kind      : "method",
                            name      : metaMethod.name,
                            returnType: metaMethod.returnType.name,
                            params    : metaMethod.nativeParameterTypes*.name,
                            static    : metaMethod.static,
                            source    : "groovy"
                    ]
                }
            }
        } catch (Throwable t) {
            LOG.debug("error reading metaclass methods for class : {}, {}", className, t.toString())
        }

        def result = [fqcn: clazz.name, members: members] as Map<String, Object>

        membersCache[className] = result

        result
    }

    @Override
    List<Map<String, Object>> compile(ScriptContext scriptContext) {
        def markers = []

        try {
            new GroovyShell(groovyShellFactory.createBinding(scriptContext), groovyShellFactory.createConfiguration())
                    .parse(scriptContext.script)
        } catch (MultipleCompilationErrorsException e) {
            e.errorCollector.errors.each { error ->
                if (error instanceof SyntaxErrorMessage) {
                    def cause = error.cause

                    markers << [
                            severity       : "error",
                            message        : cause.originalMessage ?: cause.message,
                            startLineNumber: Math.max(cause.startLine, 1),
                            startColumn    : Math.max(cause.startColumn, 1),
                            endLineNumber  : Math.max(cause.endLine, Math.max(cause.startLine, 1)),
                            endColumn      : cause.endColumn > cause.startColumn ? cause.endColumn :
                                    Math.max(cause.startColumn, 1) + 1
                    ]
                } else {
                    markers << [
                            severity       : "error",
                            message        : error.toString(),
                            startLineNumber: 1,
                            startColumn    : 1,
                            endLineNumber  : 1,
                            endColumn      : 2
                    ]
                }
            }
        } catch (Throwable t) {
            // non-compilation failure (e.g. AST transform error); report as a generic marker
            markers << [
                    severity       : "error",
                    message        : t.message ?: t.toString(),
                    startLineNumber: 1,
                    startColumn    : 1,
                    endLineNumber  : 1,
                    endColumn      : 2
            ]
        }

        markers
    }

    // internals

    private Map<String, Boolean> getOrBuildClassIndex() {
        def index = classIndex

        if (index == null) {
            synchronized (this) {
                index = classIndex

                if (index == null) {
                    index = buildClassIndex()

                    classIndex = index
                }
            }
        }

        index
    }

    private Map<String, Boolean> buildClassIndex() {
        def start = System.currentTimeMillis()
        def classes = new TreeMap<String, Boolean>()

        bundleContext.bundles.each { Bundle bundle ->
            if (bundle.state in [Bundle.ACTIVE, Bundle.RESOLVED, Bundle.STARTING]) {
                def wiring = bundle.adapt(BundleWiring)

                if (wiring != null) {
                    def exportedPackages = wiring.getCapabilities("osgi.wiring.package").collect { capability ->
                        capability.attributes["osgi.wiring.package"] as String
                    } as Set

                    wiring.listResources("/", "*.class",
                            BundleWiring.LISTRESOURCES_RECURSE | BundleWiring.LISTRESOURCES_LOCAL)?.each { String resource ->
                        def className = resource.substring(0, resource.length() - 6).replace('/', '.')

                        if (!className.contains('$') && !className.endsWith("package-info")
                                && !className.endsWith("module-info")) {
                            def packageName = className.contains('.') ?
                                    className.substring(0, className.lastIndexOf('.')) : ""
                            def exported = exportedPackages.contains(packageName)

                            if (exported || !classes.containsKey(className)) {
                                classes[className] = exported
                            }
                        }
                    }
                }
            }
        }

        LOG.info("built class index with {} classes in {}ms", classes.size(),
                System.currentTimeMillis() - start)

        classes
    }
}
