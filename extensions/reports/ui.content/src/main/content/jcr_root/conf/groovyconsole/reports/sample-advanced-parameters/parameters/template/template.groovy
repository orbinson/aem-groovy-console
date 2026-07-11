def options = report.options()

// Dynamic options: return value/label pairs. This can run any query or logic; here we list the
// editable templates under /conf so the value (path) differs from the visible label (title).
resourceResolver.findResources(
        "SELECT * FROM [cq:Template] WHERE ISDESCENDANTNODE('/conf')", "JCR-SQL2").each { template ->
    def title = template.valueMap.get("jcr:content/jcr:title", template.name)
    options.add(template.path, title as String)
}

options
