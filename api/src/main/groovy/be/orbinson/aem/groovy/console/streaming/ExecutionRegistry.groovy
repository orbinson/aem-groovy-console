package be.orbinson.aem.groovy.console.streaming

import be.orbinson.aem.groovy.console.api.context.ScriptContext
import org.osgi.annotation.versioning.ProviderType

/**
 * Runs script contexts asynchronously on a background thread and exposes their output incrementally for
 * streaming consoles.  The registry takes ownership of the context's resource resolver (which must be a
 * detached clone, not the request resolver) and closes it when the execution finishes.
 */
@ProviderType
interface ExecutionRegistry {

    /**
     * Start an asynchronous execution of the given context.
     *
     * @param scriptContext context to execute; its resource resolver is closed when the execution finishes
     * @return execution id for polling
     */
    String start(ScriptContext scriptContext)

    /**
     * Start an asynchronous execution of the given context, invoking the callback on the worker thread once
     * the script finishes (with the context's resolver still open) for post-processing such as persisting a
     * result.
     *
     * @param scriptContext context to execute; its resource resolver is closed after the callback returns
     * @param callback completion callback, or null
     * @return execution id for polling
     */
    String start(ScriptContext scriptContext, ExecutionCallback callback)

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
