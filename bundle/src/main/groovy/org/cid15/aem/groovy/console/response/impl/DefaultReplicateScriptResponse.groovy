package org.cid15.aem.groovy.console.response.impl

import groovy.transform.TupleConstructor
import org.cid15.aem.groovy.console.response.ReplicateScriptResponse

@TupleConstructor
class DefaultReplicateScriptResponse implements ReplicateScriptResponse {
    String result
}
