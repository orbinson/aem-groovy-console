# Bindings and Methods

<!-- markdownlint-disable MD060 -->

The AEM Groovy Console provides a set of default binding variables and methods that are available in every script. These
are provided by the built-in extension providers and can be supplemented by custom extensions (see the
[Extensions](../README.md#extensions) section in the README).

## Session Context

The `resourceResolver` and `session` bindings are derived from the **script context** used during execution:

- **Web UI / HTTP requests** — the session is bound to the **authenticated user** making the request. Scripts run with
  the permissions of that user.
- **Programmatic execution** (e.g., via [AECU](https://github.com/valtech/aem-easy-content-upgrade)) — the session
  depends on the calling code. Typically a service resource resolver is used.
- **Scheduled jobs / Distributed execution** — scripts run using the `aem-groovy-console-service` service user.

## Binding Variables

### Sling Bindings

| Variable           | Type                                         | Description                                                                                           |
|--------------------|----------------------------------------------|-------------------------------------------------------------------------------------------------------|
| `resourceResolver` | `ResourceResolver`                           | Sling Resource Resolver for accessing repository resources.                                           |
| `bundleContext`    | `BundleContext`                               | OSGi Bundle Context for service lookup and bundle management.                                         |
| `log`              | `Logger` (SLF4J)                             | Logger instance for writing to the AEM log during script execution.                                   |
| `out`              | `PrintStream`                                | Print stream for script output, displayed in the console output panel.                                |
| `slingRequest`     | `SlingHttpServletRequest`                    | The HTTP request object. Only available when the script is executed via the web UI or HTTP servlet.    |
| `slingResponse`    | `SlingHttpServletResponse`                   | The HTTP response object. Only available when the script is executed via the web UI or HTTP servlet.   |
| `data`             | Parsed JSON object or `String`               | Request body data passed to script execution. Automatically parsed as JSON if valid, otherwise bound as a String. Only present when data is provided. |

### JCR Bindings

| Variable      | Type                                                              | Description                                                        |
|---------------|-------------------------------------------------------------------|--------------------------------------------------------------------|
| `session`     | [`Session`](https://developer.adobe.com/experience-manager/reference-materials/spec/javax.jcr/javadocs/jcr-2.0/javax/jcr/Session.html) | JCR Session for direct repository access and node manipulation.    |
| `nodeBuilder` | [`NodeBuilder`](https://orbinson.github.io/aem-groovy-console/be/orbinson/aem/groovy/console/builders/NodeBuilder.html) | Builder for creating JCR node hierarchies using Groovy DSL syntax. |

### AEM Bindings

| Variable      | Type                                                              | Description                                                      |
|---------------|-------------------------------------------------------------------|------------------------------------------------------------------|
| `pageManager` | `PageManager`                                                     | AEM Page Manager for page operations and lookups.                |
| `pageBuilder` | [`PageBuilder`](https://orbinson.github.io/aem-groovy-console/be/orbinson/aem/groovy/console/builders/PageBuilder.html) | Builder for creating AEM page hierarchies using Groovy DSL syntax. |

## Methods

Methods are added to the script class at runtime via metaclass extension providers. They can be called as top-level
functions in any script.

### JCR Methods

| Method | Description |
|--------|-------------|
| `getNode(String path)` | Returns the `javax.jcr.Node` at the given path. |
| `save()` | Saves the current JCR session. |
| `move(String src).to(String dst)` | Moves a node from `src` to `dst`. Session is saved automatically. |
| `copy(String src).to(String dst)` | Copies a node from `src` to `dst`. Session is **not** saved automatically. |
| `rename(Node node).to(String newName)` | Renames a node while retaining its order among siblings. Session is saved automatically. |
| `xpathQuery(String query)` | Executes an XPath query and returns a `NodeIterator`. |
| `sql2Query(String query)` | Executes a JCR-SQL2 query and returns a `NodeIterator`. |

### OSGi Methods

| Method | Description |
|--------|-------------|
| `getService(Class serviceType)` | Returns a single OSGi service by class. |
| `getService(String className)` | Returns a single OSGi service by fully qualified class name. |
| `getServices(Class serviceType, String filter)` | Returns all OSGi services matching the type and LDAP filter. |
| `getServices(String className, String filter)` | Returns all OSGi services matching the class name and LDAP filter. |

### Sling Methods

| Method | Description |
|--------|-------------|
| `getResource(String path)` | Returns the `Resource` at the given path, or `null` if not found. |
| `getModel(String path, Class type)` | Adapts the resource at the given path to the specified Sling Model class. |
| `distribute(String path, String agentId = "publish", boolean isDeep = false)` | Distributes content via Sling Content Distribution, when available. |
| `invalidate(String path, String agentId = "publish", boolean isDeep = false)` | Invalidates content via Sling Content Distribution, when available. |
| `table { ... }` | Creates a formatted table for output. See [Table Output](#table-output) below. |

### AEM Methods

| Method | Description |
|--------|-------------|
| `getPage(String path)` | Returns the `Page` at the given path, or `null` if it does not exist. |
| `activate(String path, ReplicationOptions options = null)` | Activates (replicates) the node at the given path. Optionally accepts `ReplicationOptions`. |
| `deactivate(String path, ReplicationOptions options = null)` | Deactivates the node at the given path. Optionally accepts `ReplicationOptions`. |
| `delete(String path, ReplicationOptions options = null)` | Deletes the node at the given path via replication. Optionally accepts `ReplicationOptions`. |
| `createQuery(Map predicates)` | Creates a QueryBuilder `Query` from a predicates map. |

## Builders

### NodeBuilder

The `nodeBuilder` binding creates JCR content node hierarchies using a Groovy DSL. Node names map to child nodes, a
String argument specifies the node type, and Map arguments set node properties.

```groovy
nodeBuilder.etc {
    satirists("sling:Folder") {
        bierce(firstName: "Ambrose", lastName: "Bierce", birthDate: Calendar.instance)
        mencken(firstName: "H.L.", lastName: "Mencken")
    }
}
```

### PageBuilder

The `pageBuilder` binding creates AEM `cq:Page` hierarchies. A String argument sets the page title (not the node
type). Descendants of `jcr:content` nodes are treated as regular JCR nodes.

```groovy
pageBuilder.content {
    beer {
        styles("Styles") {
            "jcr:content"("jcr:lastModifiedBy": "me") {
                data("sling:Folder")
            }
            dubbel("Dubbel")
        }
    }
}
```

## Table Output

The `table` method creates structured tabular output displayed in the console.

```groovy
table {
    columns "Path", "Title", "Template"
    row "/content/site/en", "English", "page"
    row "/content/site/fr", "French", "page"
}
```

The closure receives a `Table` instance with the following methods:

| Method | Description |
|--------|-------------|
| `columns(String... names)` | Defines column headers. Must be called before adding rows. |
| `row(String... values)` | Adds a single row. The number of values must match the number of columns. |
| `rows(List<List<String>> rows)` | Adds multiple rows at once. |
