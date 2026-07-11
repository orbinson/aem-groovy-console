import be.orbinson.aem.groovy.console.reports.data.ReportColumnType

def data = report.data()

data.column('Parameter')
data.column('Value(s)')

// echo the submitted parameters so the multi-value / tag / dynamic types are easy to inspect
data.row('names', (params.names ?: []).join(', '))
data.row('tags', (params.tags ?: []).join(', '))
data.row('template', params.template ?: '')

data
