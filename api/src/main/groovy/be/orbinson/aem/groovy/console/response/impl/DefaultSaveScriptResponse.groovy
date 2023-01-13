package be.orbinson.aem.groovy.console.response.impl

import be.orbinson.aem.groovy.console.response.SaveScriptResponse
import groovy.transform.Immutable

@Immutable
class DefaultSaveScriptResponse implements SaveScriptResponse {

    String scriptName
}
