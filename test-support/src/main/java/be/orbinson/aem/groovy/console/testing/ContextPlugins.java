package be.orbinson.aem.groovy.console.testing;

import be.orbinson.aem.groovy.console.audit.AuditService;
import be.orbinson.aem.groovy.console.configuration.ConfigurationService;
import be.orbinson.aem.groovy.console.extension.impl.DefaultExtensionService;
import be.orbinson.aem.groovy.console.extension.impl.binding.AemBindingExtensionProvider;
import be.orbinson.aem.groovy.console.extension.impl.binding.JcrBindingExtensionProvider;
import be.orbinson.aem.groovy.console.extension.impl.binding.SlingBindingExtensionProvider;
import be.orbinson.aem.groovy.console.extension.impl.metaclass.AemMetaClassExtensionProvider;
import be.orbinson.aem.groovy.console.extension.impl.metaclass.JcrMetaClassExtensionProvider;
import be.orbinson.aem.groovy.console.extension.impl.metaclass.SlingMetaClassExtensionProvider;
import be.orbinson.aem.groovy.console.extension.impl.scriptmetaclass.AemScriptMetaClassExtensionProvider;
import be.orbinson.aem.groovy.console.extension.impl.scriptmetaclass.JcrScriptMetaClassExtensionProvider;
import be.orbinson.aem.groovy.console.extension.impl.scriptmetaclass.OsgiScriptMetaClassExtensionProvider;
import be.orbinson.aem.groovy.console.extension.impl.scriptmetaclass.SlingScriptMetaClassExtensionProvider;
import be.orbinson.aem.groovy.console.extension.impl.startimport.AemStarImportExtensionProvider;
import be.orbinson.aem.groovy.console.extension.impl.startimport.JcrStarImportExtensionProvider;
import be.orbinson.aem.groovy.console.extension.impl.startimport.SlingStarImportExtensionProvider;
import be.orbinson.aem.groovy.console.impl.DefaultGroovyConsoleService;
import org.apache.sling.event.jobs.JobManager;
import org.apache.sling.testing.mock.osgi.ReferenceViolationException;
import org.apache.sling.testing.mock.osgi.context.AbstractContextPlugin;
import org.apache.sling.testing.mock.osgi.context.ContextPlugin;
import org.apache.sling.testing.mock.osgi.context.OsgiContextImpl;

import java.util.function.Supplier;

import static org.mockito.Mockito.mock;

/**
 * Sling/AEM Mocks {@link ContextPlugin}s for unit-testing AEM Groovy Console scripts.
 * <p>
 * Add {@link #GROOVY_CONSOLE} to a {@code SlingContextBuilder} (or {@code AemContextBuilder}, which extends it) to
 * wire up the same default extension stack the console uses at runtime — bindings ({@code resourceResolver},
 * {@code session}, ...), script metaclasses ({@code getNode}, {@code save}, ...) and type metaclasses
 * ({@code Node#get}/{@code set}, ...). Scripts can then be executed with {@link GroovyConsole#runScript}.
 * <p>
 * The console's core service requires {@link JobManager}, {@link ConfigurationService} and {@link AuditService};
 * this plugin auto-mocks those three when absent so the console always starts. AEM-only OSGi service such as
 * {@code QueryBuilder}, {@code Replicator}, {@code Distributor} and {@code PageManagerFactory} are <em>not</em>
 * mocked automatically. Register the AEM OSGi service(s) you need (real or mocked) before this plugin runs,
 * and add wcm.io's {@code AemContext}/uber-jar yourself, to opt into the AEM-only behaviour:
 * <pre>
 * &#64;ExtendWith(AemContextExtension.class)
 * class MyScriptsTest {
 *
 *     private final QueryBuilder queryBuilder = mock(QueryBuilder.class);
 *
 *     private final AemContext context = new AemContextBuilder()
 *             .beforeSetUp(ctx -> ctx.registerService(QueryBuilder.class, queryBuilder))
 *             .plugin(ContextPlugins.GROOVY_CONSOLE)
 *             .build();
 *
 *     &#64;Test
 *     void runsMyScript() {
 *         context.create().page("/content/site");
 *         RunScriptResponse response = GroovyConsole.runScript(context, "println createQuery([path: '/content/site']).result.hits.size()");
 *         assertTrue(response.getExceptionStackTrace().isEmpty());
 *     }
 * }
 * </pre>
 */
public final class ContextPlugins {

    public static final ContextPlugin<OsgiContextImpl> GROOVY_CONSOLE = new AbstractContextPlugin<OsgiContextImpl>() {
        @Override
        public void afterSetUp(OsgiContextImpl context) {
            registerConsole(context);
        }
    };

    private ContextPlugins() {
        // constants only
    }

    private static void registerConsole(OsgiContextImpl context) {
        // OSGi Services the console's core service requires to activate at all. Only stubbed when the consumer has
        // not already registered their own, so they remain overridable.
        registerMockIfAbsent(context, JobManager.class);
        registerMockIfAbsent(context, ConfigurationService.class);
        registerMockIfAbsent(context, AuditService.class);

        context.registerInjectActivateService(new DefaultExtensionService());

        // Bindings: resourceResolver, session, nodeBuilder, log, out, ...
        context.registerInjectActivateService(new SlingBindingExtensionProvider());
        context.registerInjectActivateService(new JcrBindingExtensionProvider());

        // Script metaclass methods: getResource/getModel/table, getNode/save/move, getService(s), ...
        context.registerInjectActivateService(new SlingScriptMetaClassExtensionProvider());
        context.registerInjectActivateService(new JcrScriptMetaClassExtensionProvider());
        context.registerInjectActivateService(new OsgiScriptMetaClassExtensionProvider());

        // Type metaclasses: Node#get/set/recurse, ...
        context.registerInjectActivateService(new SlingMetaClassExtensionProvider());
        context.registerInjectActivateService(new JcrMetaClassExtensionProvider());

        // Star imports so scripts can use unqualified Sling/JCR class names.
        context.registerInjectActivateService(new SlingStarImportExtensionProvider());
        context.registerInjectActivateService(new JcrStarImportExtensionProvider());
        context.registerInjectActivateService(new AemStarImportExtensionProvider());

        // AEM-only providers reference services (QueryBuilder, Replicator, Distributor, PageManagerFactory)
        // that only exist when AEM is present.
        registerIfSatisfied(context, AemBindingExtensionProvider::new);
        registerIfSatisfied(context, AemScriptMetaClassExtensionProvider::new);
        registerIfSatisfied(context, AemMetaClassExtensionProvider::new);

        context.registerInjectActivateService(new DefaultGroovyConsoleService());
    }

    private static <T> void registerMockIfAbsent(OsgiContextImpl context, Class<T> type) {
        if (context.getService(type) == null) {
            context.registerService(type, mock(type));
        }
    }

    private static void registerIfSatisfied(OsgiContextImpl context, Supplier<Object> serviceFactory) {
        try {
            context.registerInjectActivateService(serviceFactory.get());
        } catch (ReferenceViolationException ignored) {
            // one of the service's mandatory AEM OSGi services is not registered on this context
        } catch (LinkageError ignored) {
            // AEM classes referenced by the service's fields (e.g. QueryBuilder, Replicator, PageManagerFactory)
            // are not on the classpath (no AEM/uber-jar dependency)
        }
    }
}
