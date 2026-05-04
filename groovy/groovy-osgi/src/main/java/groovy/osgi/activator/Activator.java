package groovy.osgi.activator;

import groovy.lang.GroovyRuntimeException;
import groovy.lang.GroovySystem;
import groovy.lang.MetaMethod;
import org.codehaus.groovy.reflection.CachedClass;
import org.codehaus.groovy.runtime.m12n.*;
import org.codehaus.groovy.runtime.metaclass.MetaClassRegistryImpl;
import org.codehaus.groovy.util.FastArray;
import org.osgi.framework.*;
import org.osgi.framework.namespace.HostNamespace;
import org.osgi.framework.wiring.BundleRevision;
import org.osgi.framework.wiring.BundleWire;
import org.osgi.framework.wiring.BundleWiring;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * An OSGi bundle activator for Groovy which adapts the {@link org.codehaus.groovy.runtime.m12n.ExtensionModuleScanner}
 * to the OSGi environment.
 *
 * <p>Since Groovy 4.0.23 several extensions (groovy-dateutil, groovy-json, groovy-nio,
 * groovy-templates, groovy-xml, groovy-yaml) ship as <em>fragment</em> bundles attached to the
 * {@code groovy} host. Fragments do not own a class loader, so we walk the host wire to obtain
 * the classloader required to instantiate the extension classes.</p>
 */
public class Activator implements BundleActivator, SynchronousBundleListener {

    private static final Logger LOG = LoggerFactory.getLogger(Activator.class);

    private final ConcurrentMap<Long, BundleWrapper> bundleWrappers = new ConcurrentHashMap<>();
    private final Map<CachedClass, List<MetaMethod>> map = new HashMap<>();

    @Override
    public synchronized void start(BundleContext bundleContext) throws Exception {
        LOG.debug("activating");
        bundleContext.addBundleListener(this);
        LOG.debug("checking existing bundles");
        for (Bundle bundle : bundleContext.getBundles()) {
            if (bundle.getState() == Bundle.RESOLVED || bundle.getState() == Bundle.STARTING ||
                    bundle.getState() == Bundle.ACTIVE || bundle.getState() == Bundle.STOPPING) {
                register(bundle);
            }
        }
        LOG.debug("activated");
    }

    @Override
    public synchronized void stop(BundleContext bundleContext) throws Exception {
        LOG.debug("deactivating");
        bundleContext.removeBundleListener(this);
        while (!bundleWrappers.isEmpty()) {
            unregister(bundleWrappers.keySet().iterator().next());
        }
        LOG.debug("deactivated");
    }

    // ================================================================
    // SynchronousBundleListener interface impl
    // ================================================================

    @Override
    public void bundleChanged(BundleEvent event) {
        if (event.getType() == BundleEvent.RESOLVED) {
            register(event.getBundle());
        } else if (event.getType() == BundleEvent.UNRESOLVED || event.getType() == BundleEvent.UNINSTALLED) {
            unregister(event.getBundle().getBundleId());
        }
    }

    protected void register(final Bundle bundle) {
        LOG.debug("checking bundle " + bundle.getBundleId() + " (" + bundle.getSymbolicName() + ")");
        if (containsExtensionModule(bundle) && hasCorrectMetaClassRegistry()) {
            LOG.info("Registering extension module from bundle {} ({})", bundle.getBundleId(), bundle.getSymbolicName());
            try {
                registerExtensionModule(bundle);
            } catch (IOException e) {
                LOG.error("Could not register extension module from bundle " + bundle.getSymbolicName(), e);
            } catch (RuntimeException e) {
                LOG.error("Could not register extension module from bundle " + bundle.getSymbolicName(), e);
            }
        }
    }

    private boolean hasCorrectMetaClassRegistry() {
        return GroovySystem.getMetaClassRegistry() instanceof MetaClassRegistryImpl;
    }

    protected void unregister(long bundleId) {
        BundleWrapper bundleWrapper = bundleWrappers.remove(bundleId);
        if (bundleWrapper != null) {
            try {
                unregisterExtensionModule(bundleWrapper.bundle);
            } catch (IOException e) {
                LOG.error("Could not unregister extension module", e);
            }
        }
    }

    private boolean containsExtensionModule(Bundle bundle) {
        return bundle.getEntry(ExtensionModuleScanner.MODULE_META_INF_FILE) != null ||
                bundle.getEntry(ExtensionModuleScanner.LEGACY_MODULE_META_INF_FILE) != null;
    }

    private void registerExtensionModule(Bundle bundle) throws IOException {
        Properties properties = loadProperties(getExtensionModuleUrl(bundle));
        String moduleName = properties.getProperty(PropertiesModuleFactory.MODULE_NAME_KEY);
        ClassLoader classLoader = resolveClassLoader(bundle);
        if (classLoader == null) {
            LOG.warn("No classloader available for bundle {} (id={}); skipping extension module {}",
                    bundle.getSymbolicName(), bundle.getBundleId(), moduleName);
            return;
        }

        MetaClassRegistryImpl registry = (MetaClassRegistryImpl) GroovySystem.getMetaClassRegistry();
        ExtensionModuleRegistry moduleRegistry = registry.getModuleRegistry();

        // If the module is already in the registry — typically because Groovy's own
        // ExtensionModuleScanner ran at MetaClassRegistry init with a stale classloader — drop it
        // and rebuild with our bundle's wiring. This is necessary on Groovy 4.0.23+ where extension
        // descriptors live in fragment bundles whose classloaders only become available after the
        // host's wiring resolves.
        if (moduleRegistry.hasModule(moduleName)) {
            ExtensionModule existing = moduleRegistry.getModule(moduleName);
            LOG.info("Re-registering extension module {} from {} (was registered by Groovy scanner; refreshing with bundle classloader)",
                    moduleName, bundle.getSymbolicName());
            moduleRegistry.removeModule(existing);
        } else {
            LOG.info("Registering extension module {} from {} using classloader {}",
                    moduleName, bundle.getSymbolicName(), classLoader);
        }

        ExtensionModule extensionModule = MetaInfExtensionModule.newModule(properties, classLoader);
        FastArray instanceMethods = registry.getInstanceMethods();
        FastArray staticMethods = registry.getStaticMethods();
        registerExtensionModule(extensionModule, moduleRegistry, instanceMethods, staticMethods);
        bundleWrappers.put(bundle.getBundleId(), new BundleWrapper(bundle, extensionModule));
    }

    /**
     * Returns the classloader to use when loading the extension classes referenced in the
     * descriptor. For a regular bundle this is the bundle's own classloader; for a fragment
     * bundle (which does not have a classloader of its own) we follow the host wire and use
     * the host's classloader, which can resolve classes contributed by attached fragments.
     */
    private ClassLoader resolveClassLoader(Bundle bundle) {
        BundleWiring wiring = bundle.adapt(BundleWiring.class);
        if (wiring == null) {
            return null;
        }
        ClassLoader classLoader = wiring.getClassLoader();
        if (classLoader != null) {
            // Regular bundle, or host bundle of fragments — its classloader can see both.
            return classLoader;
        }
        // Fragment bundles do not own a classloader. Walk to the host bundle's wiring and
        // use its classloader, which exposes both the host's classes and the fragment's.
        BundleRevision revision = wiring.getRevision();
        if (revision != null && (revision.getTypes() & BundleRevision.TYPE_FRAGMENT) != 0) {
            List<BundleWire> hostWires = wiring.getRequiredWires(HostNamespace.HOST_NAMESPACE);
            if (hostWires != null) {
                for (BundleWire hostWire : hostWires) {
                    BundleWiring hostWiring = hostWire.getProviderWiring();
                    if (hostWiring != null && hostWiring.getClassLoader() != null) {
                        return hostWiring.getClassLoader();
                    }
                }
            }
        }
        return null;
    }

    private void registerExtensionModule(ExtensionModule module, ExtensionModuleRegistry moduleRegistry, FastArray instanceMethods, FastArray staticMethods) {
        // Caller already removed any pre-existing module with the same name.
        moduleRegistry.addModule(module);
        // register MetaMethods
        List<MetaMethod> metaMethods = module.getMetaMethods();
        for (MetaMethod metaMethod : metaMethods) {
            CachedClass cachedClass = metaMethod.getDeclaringClass();
            List<MetaMethod> methods = map.computeIfAbsent(cachedClass, k -> new ArrayList<MetaMethod>(4));
            methods.add(metaMethod);
            if (metaMethod.isStatic()) {
                staticMethods.add(metaMethod);
            } else {
                instanceMethods.add(metaMethod);
            }
        }

        for (Map.Entry<CachedClass, List<MetaMethod>> e : map.entrySet()) {
            CachedClass cls = e.getKey();
            cls.setNewMopMethods(e.getValue());
        }
    }

    private void unregisterExtensionModule(Bundle bundle) throws IOException {
        Properties properties = loadProperties(getExtensionModuleUrl(bundle));
        String moduleName = properties.getProperty(PropertiesModuleFactory.MODULE_NAME_KEY);
        ExtensionModuleRegistry extensionModuleRegistry = (((MetaClassRegistryImpl) GroovySystem.getMetaClassRegistry()).getModuleRegistry());
        if (extensionModuleRegistry.hasModule(moduleName) && bundleWrappers.get(bundle.getBundleId()) != null) {
            extensionModuleRegistry.removeModule(bundleWrappers.get(bundle.getBundleId()).extensionModule);
            // What should we do with the cached classes?
        }
    }

    private static URL getExtensionModuleUrl(Bundle bundle) {
        URL url = bundle.getEntry(ExtensionModuleScanner.MODULE_META_INF_FILE);
        if (url == null) {
            return bundle.getEntry(ExtensionModuleScanner.LEGACY_MODULE_META_INF_FILE);
        }
        return url;
    }

    private Properties loadProperties(URL resource) throws IOException {
        try (InputStream in = resource.openStream()) {
            Properties properties = new Properties();
            properties.load(in);
            return properties;
        }
    }

    private static class BundleWrapper {
        private final Bundle bundle;
        private final ExtensionModule extensionModule;

        public BundleWrapper(Bundle bundle, ExtensionModule extensionModule) {
            this.bundle = bundle;
            this.extensionModule = extensionModule;
        }
    }
}
