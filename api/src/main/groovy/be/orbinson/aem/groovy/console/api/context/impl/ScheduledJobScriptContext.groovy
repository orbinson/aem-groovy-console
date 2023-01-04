package be.orbinson.aem.groovy.console.api.context.impl

import be.orbinson.aem.groovy.console.api.JobProperties
import be.orbinson.aem.groovy.console.api.context.JobScriptContext
import groovy.transform.TupleConstructor
import org.apache.sling.api.resource.ResourceResolver

@TupleConstructor
class ScheduledJobScriptContext implements JobScriptContext {

    ResourceResolver resourceResolver

    ByteArrayOutputStream outputStream

    PrintStream printStream

    String jobId

    JobProperties jobProperties

    @Override
    String getScript() {
        jobProperties.script
    }

    @Override
    String getData() {
        jobProperties.data
    }

    @Override
    String getUserId() {
        resourceResolver.userID
    }
}
