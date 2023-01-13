package be.orbinson.aem.groovy.console.api.context

import be.orbinson.aem.groovy.console.api.JobProperties
import org.osgi.annotation.versioning.ConsumerType

/**
 * Script context for scheduled jobs.
 */
@ConsumerType
interface JobScriptContext extends ScriptContext {

    /**
     *
     * @return
     */
    String getJobId()

    /**
     *
     * @return
     */
    JobProperties getJobProperties()
}
