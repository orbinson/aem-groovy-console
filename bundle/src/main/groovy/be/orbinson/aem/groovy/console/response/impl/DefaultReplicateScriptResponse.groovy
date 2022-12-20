package be.orbinson.aem.groovy.console.response.impl

import be.orbinson.aem.groovy.console.response.ReplicateScriptResponse
import groovy.transform.TupleConstructor

@TupleConstructor
class DefaultReplicateScriptResponse implements ReplicateScriptResponse {
    String result
}
