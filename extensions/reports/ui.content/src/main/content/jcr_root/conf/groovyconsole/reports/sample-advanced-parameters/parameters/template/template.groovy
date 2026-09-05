def options = report.options()

// Dynamic options: return value/label pairs. This can run any query or logic; here we list the child
// resources of /content — which exist on both AEM and plain Sling — so the value (path) differs from the
// visible label (name).
resourceResolver.getResource("/content")?.listChildren()?.each { child ->
    options.add(child.path, child.name)
}

options
