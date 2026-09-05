package be.orbinson.aem.groovy.console.reports.impl

import be.orbinson.aem.groovy.console.reports.context.ReportScriptContext
import groovy.json.JsonBuilder
import groovy.transform.TupleConstructor
import org.apache.sling.api.resource.ResourceResolver

/**
 * Script context for report executions.  Scripts run with the resource resolver of the requesting user so
 * their JCR ACLs apply.
 */
@TupleConstructor
class DefaultReportScriptContext implements ReportScriptContext {

    String reportName

    Map<String, Object> parameterValues

    ResourceResolver resourceResolver

    ByteArrayOutputStream outputStream

    PrintStream printStream

    String script

    String userId

    @Override
    String getData() {
        new JsonBuilder(parameterValues ?: [:]).toString()
    }
}
