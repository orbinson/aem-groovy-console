package be.orbinson.aem.groovy.console.audit

import be.orbinson.aem.groovy.console.response.RunScriptResponse
import be.orbinson.aem.groovy.console.response.impl.DefaultRunScriptResponse
import org.apache.jackrabbit.util.Text
import groovy.transform.ToString
import org.apache.sling.api.resource.Resource

@ToString(includePackage = false, includes = ["path"])
class AuditRecord implements RunScriptResponse {

    private static final Integer DEPTH_RELATIVE_PATH = 3

    private final String path

    @Delegate
    private final RunScriptResponse response

    AuditRecord(Resource resource) {
        path = resource.path
        response = DefaultRunScriptResponse.fromAuditRecordResource(resource)
    }

    // explicit (non-final) getters: a `final` field here would otherwise make Groovy generate a `final`
    // accessor, which bnd-baseline treats as a MAJOR breaking change to this exported package if the
    // build toolchain ever flips it relative to a previously released jar (see git history for details)
    String getPath() {
        path
    }

    RunScriptResponse getResponse() {
        response
    }

    String getDownloadUrl() {
        def downloadUrl = null

        if (output) {
            downloadUrl = "/bin/groovyconsole/download?userId=$userId&script=$relativePath"
        }

        downloadUrl
    }

    String getRelativePath() {
        (path - Text.getAbsoluteParent(path, DEPTH_RELATIVE_PATH)).substring(1)
    }

    String getException() {
        def exception = ""

        if (exceptionStackTrace) {
            def firstLine = exceptionStackTrace.readLines().first()

            if (firstLine.contains(":")) {
                exception = firstLine.substring(0, firstLine.indexOf(":"))
            } else {
                exception = firstLine
            }
        }

        exception
    }
}
