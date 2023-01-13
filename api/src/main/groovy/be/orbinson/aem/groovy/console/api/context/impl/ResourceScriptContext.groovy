package be.orbinson.aem.groovy.console.api.context.impl

import be.orbinson.aem.groovy.console.api.context.ScriptContext
import groovy.transform.TupleConstructor
import org.apache.sling.api.resource.ResourceResolver

@TupleConstructor
class ResourceScriptContext implements ScriptContext {
    ResourceResolver resourceResolver

    ByteArrayOutputStream outputStream

    PrintStream printStream

    String script

    String data

    @Override
    public String getUserId() {
        return resourceResolver.userID
    }
}
