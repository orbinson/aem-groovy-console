package be.orbinson.aem.groovy.console.streaming

import org.apache.sling.api.resource.ResourceResolver

/**
 * Runs scripts asynchronously and exposes their output incrementally for streaming consoles.
 */
interface ExecutionRegistry {

    /**
     * Start an asynchronous script execution.  The registry takes ownership of the given resolver
     * (a clone of the requesting user's resolver) and closes it when the execution finishes.
     *
     * @param resourceResolver cloned resource resolver to execute with
     * @param script Groovy script content
     * @param data optional JSON/string data bound to the script
     * @return execution id for polling
     */
    String start(ResourceResolver resourceResolver, String script, String data)

    /**
     * Poll an execution for new output.
     *
     * @param executionId execution id returned by start
     * @param offset character offset already received by the client
     * @return map with chunk (new output), offset (next offset), done flag, and — once done —
     *         the full run response; null if the execution id is unknown or expired
     */
    Map<String, Object> poll(String executionId, int offset)
}
