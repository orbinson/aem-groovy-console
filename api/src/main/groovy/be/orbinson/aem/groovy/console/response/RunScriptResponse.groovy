package be.orbinson.aem.groovy.console.response

import be.orbinson.aem.groovy.console.api.JobProperties
import org.osgi.annotation.versioning.ConsumerType

/**
 * Response for script executions.
 */
@ConsumerType
interface RunScriptResponse {

    /**
     * Get the date of script execution.
     *
     * @return execution date
     */
    Calendar getDate()

    String getScript()

    String getData()

    String getResult()

    String getOutput()

    String getExceptionStackTrace()

    String getRunningTime()

    String getUserId()

    String getJobId()

    JobProperties getJobProperties()

    String getMediaType()

    String getOutputFileName()
}
