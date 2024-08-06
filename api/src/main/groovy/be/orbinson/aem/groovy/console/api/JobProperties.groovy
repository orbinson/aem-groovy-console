package be.orbinson.aem.groovy.console.api


import groovy.transform.TupleConstructor
import org.apache.commons.collections4.SetUtils
import org.apache.sling.api.SlingHttpServletRequest
import org.apache.sling.api.resource.ValueMap
import org.apache.sling.event.jobs.Job

import static be.orbinson.aem.groovy.console.constants.GroovyConsoleConstants.*

@TupleConstructor
class JobProperties {

    private static final Set<String> ALL_JOB_PROPERTIES = SetUtils.union(JOB_PROPERTIES, Set.of(DATE_CREATED, SCHEDULED_JOB_ID)).toSet()

    Map<String, Object> properties

    static JobProperties fromRequest(SlingHttpServletRequest request) {
        def properties = JOB_PROPERTIES.collectEntries { propertyName ->
            [propertyName, request.getParameter(propertyName)]
        }.findAll { it.value != null } as Map<String, Object>

        properties[DATE_CREATED] = Calendar.instance
        properties[SCHEDULED_JOB_ID] = UUID.randomUUID().toString()

        new JobProperties(properties)
    }

    static JobProperties fromJob(Job job) {
        def properties = [:] as Map<String, Object>

        job.propertyNames.findAll { propertyName -> ALL_JOB_PROPERTIES.contains(propertyName) }
                .each { propertyName ->
                    properties[propertyName] = job.getProperty(propertyName)
                }

        new JobProperties(properties)
    }

    static JobProperties fromValueMap(ValueMap valueMap) {
        def properties = [:] as Map<String, Object>

        valueMap.each { name, value ->
            if (ALL_JOB_PROPERTIES.contains(name)) {
                properties[name] = value
            }
        }

        new JobProperties(properties)
    }

    String getScheduledJobId() {
        properties.get(SCHEDULED_JOB_ID)
    }

    Set<String> getEmailTo() {
        def emailTo = properties.get(EMAIL_TO) as String

        (emailTo ? emailTo.tokenize(",")*.trim() : []) as Set
    }

    String getJobTitle() {
        properties.get(JOB_TITLE)
    }

    String getJobDescription() {
        properties.get(JOB_DESCRIPTION)
    }

    String getScript() {
        properties.get(SCRIPT)
    }

    String getData() {
        properties.get(DATA)
    }

    String getCronExpression() {
        properties.get(CRON_EXPRESSION)
    }

    String getMediaType() {
        properties.get(MEDIA_TYPE)
    }

    Map<String, Object> toMap() {
        properties
    }
}