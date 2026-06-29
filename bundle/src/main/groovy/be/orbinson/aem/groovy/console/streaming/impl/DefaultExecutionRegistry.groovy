package be.orbinson.aem.groovy.console.streaming.impl

import be.orbinson.aem.groovy.console.GroovyConsoleService
import be.orbinson.aem.groovy.console.api.context.ScriptContext
import be.orbinson.aem.groovy.console.response.RunScriptResponse
import be.orbinson.aem.groovy.console.streaming.ExecutionCallback
import be.orbinson.aem.groovy.console.streaming.ExecutionRegistry
import groovy.util.logging.Slf4j
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Deactivate
import org.osgi.service.component.annotations.Reference

import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@Component(service = ExecutionRegistry, immediate = true)
@Slf4j("LOG")
class DefaultExecutionRegistry implements ExecutionRegistry {

    /** Completed executions are kept available for polling for this long. */
    private static final long RETENTION_MILLIS = TimeUnit.MINUTES.toMillis(10)

    @Reference
    private GroovyConsoleService groovyConsoleService

    private final Map<String, Execution> executions = new ConcurrentHashMap<>()

    private ExecutorService executor

    @Activate
    void activate() {
        executor = Executors.newCachedThreadPool()
    }

    @Deactivate
    void deactivate() {
        executor?.shutdownNow()
        executions.clear()
    }

    @Override
    String start(ScriptContext scriptContext) {
        start(scriptContext, null)
    }

    @Override
    String start(ScriptContext scriptContext, ExecutionCallback callback) {
        evictExpired()

        def executionId = UUID.randomUUID().toString()
        def execution = new Execution(scriptContext: scriptContext)

        executions[executionId] = execution

        executor.submit {
            def start = System.currentTimeMillis()

            try {
                def response = groovyConsoleService.runScript(scriptContext)
                execution.response = response

                if (callback != null) {
                    // run post-processing while the resolver is still open
                    callback.onComplete(response, System.currentTimeMillis() - start)
                }
            } catch (Throwable t) {
                LOG.error("error running async script execution : {}", executionId, t)
            } finally {
                execution.done = true
                execution.finishedAt = System.currentTimeMillis()

                try {
                    scriptContext.resourceResolver.close()
                } catch (Throwable ignored) {
                    // resolver already closed
                }
            }
        }

        LOG.debug("started async script execution : {}", executionId)

        executionId
    }

    @Override
    Map<String, Object> poll(String executionId, int offset) {
        def execution = executions[executionId]

        if (!execution) {
            return null
        }

        def output = execution.scriptContext.outputStream.toString(StandardCharsets.UTF_8.name())
        def safeOffset = Math.min(Math.max(offset, 0), output.length())

        def result = [
                chunk : output.substring(safeOffset),
                offset: output.length(),
                done  : execution.done
        ] as Map<String, Object>

        if (execution.done) {
            result.response = execution.response
        }

        result
    }

    private void evictExpired() {
        def now = System.currentTimeMillis()

        executions.entrySet().removeAll { entry ->
            entry.value.done && entry.value.finishedAt && (now - entry.value.finishedAt) > RETENTION_MILLIS
        }
    }

    private static class Execution {

        ScriptContext scriptContext

        volatile RunScriptResponse response

        volatile boolean done

        volatile Long finishedAt
    }
}
