# Extensions

## Extension Hooks

The Groovy Console provides extension hooks to further customize script execution. The console provides an API
containing extension provider interfaces that can be implemented as OSGi services in any bundle deployed to an AEM
instance. See the default extension providers in the `be.orbinson.aem.groovy.console.extension.impl` package for
examples of how a bundle can implement these services to supply additional script bindings, compilation customizers,
metaclasses, and star imports.

| Service Interface                                                           | Description                                                                                                                  |
|-----------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------|
| `be.orbinson.aem.groovy.console.api.BindingExtensionProvider`               | Customize the bindings that are provided for each script execution.                                                          |
| `be.orbinson.aem.groovy.console.api.CompilationCustomizerExtensionProvider` | Restrict language features (via blacklist or whitelist) or provide AST transformations within the Groovy script compilation. |
| `be.orbinson.aem.groovy.console.api.ScriptMetaClassExtensionProvider`       | Add runtime metaclasses (i.e. new methods) to the underlying script class.                                                   |
| `be.orbinson.aem.groovy.console.api.StarImportExtensionProvider`            | Supply additional star imports that are added to the compiler configuration for each script execution.                       |

## Registering Additional Metaclasses

Services implementing the `be.orbinson.aem.groovy.console.extension.MetaClassExtensionProvider` will be automatically
discovered and bound by the OSGi container. These services can be implemented in any deployed bundle. The AEM Groovy
Extension bundle will handle the registration and removal of supplied metaclasses as these services are
activated/deactivated in the container. See the `DefaultMetaClassExtensionProvider` service for the proper closure
syntax for registering metaclasses.

## Notifications

To provide custom notifications for script executions, bundles may implement
the `be.orbinson.aem.groovy.console.notification.NotificationService` interface (see
the `be.orbinson.aem.groovy.console.notification.impl.EmailNotificationService` class for an example). These services
will
be dynamically bound by the Groovy Console service and all registered notification services will be called for each
script execution.
