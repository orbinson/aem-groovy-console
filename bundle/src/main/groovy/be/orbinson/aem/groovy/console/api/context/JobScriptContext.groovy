package be.orbinson.aem.groovy.console.api.context

import be.orbinson.aem.groovy.console.api.JobProperties

/**
 * Script context for scheduled jobs.
 */
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