package org.cid15.aem.groovy.console.api.impl

import groovy.transform.TupleConstructor
import org.apache.sling.api.resource.ResourceResolver
import org.cid15.aem.groovy.console.api.context.ScriptContext

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
