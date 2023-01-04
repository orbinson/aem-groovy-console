package be.orbinson.aem.groovy.console.response

import org.osgi.annotation.versioning.ConsumerType

/**
 * Response for replicated scripts.
 */
@ConsumerType
interface ReplicateScriptResponse {

    String getResult()

}
