package com.icfolson.aem.groovy.console.audit

import com.day.text.Text
import com.icfolson.aem.groovy.console.response.RunScriptResponse
import groovy.transform.ToString
import org.apache.sling.api.resource.Resource

import javax.jcr.Node

import static com.day.cq.commons.jcr.JcrConstants.JCR_CREATED
import static com.icfolson.aem.groovy.console.constants.GroovyConsoleConstants.JOB_ID
import static com.icfolson.aem.groovy.console.constants.GroovyConsoleConstants.MEDIA_TYPE

@ToString(includePackage = false, includes = ["path"])
class AuditRecord {

    private static final Integer DEPTH_USER_ID = 4

    private static final Integer DEPTH_RELATIVE_PATH = 3

    private Resource resource

    @Delegate
    final RunScriptResponse response

    final String path

    final Calendar date

    AuditRecord(Resource resource) {
        this.resource = resource

        path = resource.path
        date = resource.valueMap.get(JCR_CREATED, Calendar)
        response = RunScriptResponse.fromAuditRecordResource(resource)
    }

    String getUserId() {
        resource.adaptTo(Node).getAncestor(DEPTH_USER_ID).name
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

    String getJobId() {
        resource.valueMap.get(JOB_ID, String)
    }

    String getMediaType() {
        resource.valueMap.get(MEDIA_TYPE, String)
    }
}
