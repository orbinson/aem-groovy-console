import be.orbinson.aem.groovy.console.reports.data.ReportColumnType

def data = report.data()

data.column('Name')
data.column('Path', ReportColumnType.LINK)
data.column('Type')
// UI-only column: shown in the table but omitted from the CSV/XLSX export (exported = false)
data.column('Edit', ReportColumnType.LINK, false)

def resource = resourceResolver.getResource(params.path)

resource?.listChildren()?.each { child ->
    data.row(
        child.name,
        [text: child.name, href: child.path],
        child.resourceType,
        [text: 'Edit', href: "/editor.html${child.path}.html"]
    )
}

data
