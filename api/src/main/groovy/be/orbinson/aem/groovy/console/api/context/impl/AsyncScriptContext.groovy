package be.orbinson.aem.groovy.console.api.context.impl

import be.orbinson.aem.groovy.console.api.context.ScriptContext
import groovy.transform.TupleConstructor
import org.apache.sling.api.resource.ResourceResolver

/**
 * Script context for asynchronous (streaming) executions: detached from the originating request,
 * backed by a cloned resource resolver that stays open for the lifetime of the execution.
 */
@TupleConstructor
class AsyncScriptContext implements ScriptContext {

    ResourceResolver resourceResolver

    ByteArrayOutputStream outputStream

    PrintStream printStream

    String script

    String data

    String userId
}
