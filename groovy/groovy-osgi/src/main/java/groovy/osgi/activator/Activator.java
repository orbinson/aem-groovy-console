package groovy.osgi.activator;

import groovy.lang.GroovyRuntimeException;
import groovy.lang.GroovySystem;
import groovy.lang.MetaMethod;
import org.codehaus.groovy.reflection.CachedClass;
import org.codehaus.groovy.runtime.m12n.*;
import org.codehaus.groovy.runtime.metaclass.MetaClassRegistryImpl;
import org.codehaus.groovy.util.FastArray;
import org.osgi.framework.*;
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
        LOG.debug("checking bundle " + bundle.getBundleId());
        if (containsExtensionModule(bundle) && hasCorrectMetaClassRegistry()) {
            LOG.debug("Registering bundle for extension module: " + bundle.getBundleId());
            try {
                registerExtensionModule(bundle);
            } catch (IOException e) {
                LOG.error("Could not register extension module", e);
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
        ExtensionModuleRegistry extensionModuleRegistry = (((MetaClassRegistryImpl) GroovySystem.getMetaClassRegistry()).getModuleRegistry());
        if (!extensionModuleRegistry.hasModule(moduleName)) {
            BundleWiring wiring = bundle.adapt(BundleWiring.class);
            ExtensionModule extensionModule = MetaInfExtensionModule.newModule(properties, wiring.getClassLoader());
            ExtensionModuleRegistry moduleRegistry = (((MetaClassRegistryImpl) GroovySystem.getMetaClassRegistry()).getModuleRegistry());
            FastArray instanceMethods = (((MetaClassRegistryImpl) GroovySystem.getMetaClassRegistry()).getInstanceMethods());
            FastArray staticMethods = (((MetaClassRegistryImpl) GroovySystem.getMetaClassRegistry()).getStaticMethods());
            registerExtensionModule(extensionModule, moduleRegistry, instanceMethods, staticMethods);
            bundleWrappers.put(bundle.getBundleId(), new BundleWrapper(bundle, extensionModule));
        }
    }

    private void registerExtensionModule(ExtensionModule module, ExtensionModuleRegistry moduleRegistry, FastArray instanceMethods, FastArray staticMethods) {
        if (moduleRegistry.hasModule(module.getName())) {
            ExtensionModule loadedModule = moduleRegistry.getModule(module.getName());
            if (loadedModule.getVersion().equals(module.getVersion())) {
                // already registered
                return;
            } else {
                throw new GroovyRuntimeException("Conflicting module versions. Module [" + module.getName() + " is loaded in version " +
                        loadedModule.getVersion() + " and you are trying to load version " + module.getVersion());
            }
        }
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
        if (extensionModuleRegistry.hasModule(moduleName)) {
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
