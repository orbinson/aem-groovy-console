package be.orbinson.aem.groovy.console.streaming

import be.orbinson.aem.groovy.console.response.RunScriptResponse
import org.osgi.annotation.versioning.ConsumerType

/**
 * Invoked on the execution's worker thread once an asynchronous script finishes, before its resource
 * resolver is closed, so callers can perform post-processing (e.g. parsing and persisting a result).
 */
@ConsumerType
interface ExecutionCallback {

    /**
     * @param response the run result
     * @param durationMillis wall-clock execution time in milliseconds
     */
    void onComplete(RunScriptResponse response, long durationMillis)
}
