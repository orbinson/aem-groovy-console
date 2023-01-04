package be.orbinson.aem.groovy.console.response

import org.osgi.annotation.versioning.ConsumerType

/**
 * Response for saved scripts.
 */
@ConsumerType
interface SaveScriptResponse {

    String getScriptName()
}
