package be.orbinson.aem.groovy.console.audit.impl

import be.orbinson.aem.groovy.console.audit.AuditRecord
import be.orbinson.aem.groovy.console.audit.AuditService
import be.orbinson.aem.groovy.console.configuration.ConfigurationService
import be.orbinson.aem.groovy.console.constants.GroovyConsoleConstants
import be.orbinson.aem.groovy.console.response.RunScriptResponse
import groovy.transform.Synchronized
import groovy.util.logging.Slf4j
import org.apache.jackrabbit.JcrConstants
import org.apache.jackrabbit.oak.spi.nodetype.NodeTypeConstants
import org.apache.sling.api.resource.*
import org.apache.sling.jcr.resource.api.JcrResourceConstants
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

import static be.orbinson.aem.groovy.console.constants.GroovyConsoleConstants.*

@Component(service = AuditService, immediate = true)
@Slf4j("LOG")
class DefaultAuditService implements AuditService {

    private static final String DATE_FORMAT_YEAR = "yyyy"

    private static final String DATE_FORMAT_MONTH = "MM"

    private static final String DATE_FORMAT_DAY = "dd"

    @Reference
    private ResourceResolverFactory resourceResolverFactory

    @Reference
    private ConfigurationService configurationService

    @Override
    AuditRecord createAuditRecord(RunScriptResponse response) {
        def auditRecord

        withResourceResolver { ResourceResolver resourceResolver ->
            try {
                def auditRecordResource = addAuditRecordResource(resourceResolver, response.userId)

                setAuditRecordResourceProperties(resourceResolver, auditRecordResource, response)

                auditRecord = new AuditRecord(auditRecordResource)

                LOG.debug("created audit record : {}", auditRecord)

                auditRecord
            } catch (PersistenceException e) {
                LOG.error("error creating audit record", e)

                throw e
            }
        }
    }

    @Override
    void deleteAllAuditRecords(String userId) {
        withResourceResolver { ResourceResolver resourceResolver ->
            try {
                def auditNodePath = getAuditNodePath(userId)
                def auditResource = resourceResolver.getResource(auditNodePath)

                if (auditResource) {
                    auditResource.listChildren().each { resource ->
                        resourceResolver.delete(resource)
                    }

                    LOG.debug("deleted all audit record resources for path : {}", auditNodePath)

                    resourceResolver.commit()
                } else {
                    LOG.debug("audit resource not found for user ID : {}", userId)
                }
            } catch (PersistenceException e) {
                LOG.error("error deleting audit records", e)

                throw e
            }
        }
    }

    @Override
    void deleteAuditRecord(String userId, String relativePath) {
        withResourceResolver { ResourceResolver resourceResolver ->
            try {
                def auditRecordResource = resourceResolver.getResource("$AUDIT_PATH/$userId/$relativePath")

                resourceResolver.delete(auditRecordResource)

                LOG.debug("deleted audit record for user : {} at relative path : {}", userId, relativePath)

                resourceResolver.commit()
            } catch (PersistenceException e) {
                LOG.error("error deleting audit record", e)

                throw e
            }
        }
    }

    @Override
    List<AuditRecord> getAllAuditRecords(String userId) {
        def auditNodePath = getAuditNodePath(userId)

        withResourceResolver { ResourceResolver resourceResolver ->
            findAllAuditRecords(resourceResolver, auditNodePath)
        }
    }

    @Override
    List<AuditRecord> getAllScheduledJobAuditRecords() {
        getAllAuditRecords(GroovyConsoleConstants.SYSTEM_USER_NAME)
    }

    @Override
    AuditRecord getAuditRecord(String jobId) {
        withResourceResolver { ResourceResolver resourceResolver ->
            findAllAuditRecords(resourceResolver, AUDIT_PATH).find { auditRecord ->
                auditRecord.jobId == jobId
            }
        }
    }

    @Override
    AuditRecord getAuditRecord(String userId, String relativePath) {
        def auditRecord = null

        withResourceResolver { ResourceResolver resourceResolver ->
            def auditRecordResource = resourceResolver.getResource("$AUDIT_PATH/$userId").getChild(relativePath)

            if (auditRecordResource) {
                auditRecord = new AuditRecord(auditRecordResource)

                LOG.debug("found audit record : {}", auditRecord)
            }
        }

        auditRecord
    }

    @Override
    List<AuditRecord> getAuditRecords(String userId, Calendar startDate, Calendar endDate) {
        getAuditRecordsForDateRange(getAllAuditRecords(userId), startDate, endDate)
    }

    @Override
    List<AuditRecord> getScheduledJobAuditRecords(Calendar startDate, Calendar endDate) {
        getAuditRecordsForDateRange(allScheduledJobAuditRecords, startDate, endDate)
    }

    @Synchronized
    private Resource addAuditRecordResource(ResourceResolver resourceResolver, String userId) {
        def date = Calendar.instance
        def year = date.format(DATE_FORMAT_YEAR)
        def month = date.format(DATE_FORMAT_MONTH)
        def day = date.format(DATE_FORMAT_DAY)

        def auditRecordFolderResource = ResourceUtil.getOrCreateResource(resourceResolver, "$AUDIT_PATH/$userId/$year/$month/$day",
                JcrResourceConstants.NT_SLING_FOLDER, JcrResourceConstants.NT_SLING_FOLDER, true)

        def auditRecordName = ResourceUtil.createUniqueChildName(auditRecordFolderResource, "record")

        def props = [
                (JcrConstants.JCR_MIXINTYPES): NodeTypeConstants.MIX_CREATED,
                (JcrConstants.JCR_CREATED)   : Calendar.getInstance()
        ]
        def auditRecordResource = ResourceUtil.getOrCreateResource(resourceResolver, auditRecordFolderResource.getPath() + "/" + auditRecordName,
                props, JcrResourceConstants.NT_SLING_FOLDER, true)

        auditRecordResource
    }

    private static void setAuditRecordResourceProperties(ResourceResolver resourceResolver, Resource auditRecordResource, RunScriptResponse response) {
        def valueMap = auditRecordResource.adaptTo(ModifiableValueMap.class);
        valueMap.put(SCRIPT, response.script)

        if (response.data) {
            valueMap.put(DATA, response.data)
        }

        if (response.jobId) {
            valueMap.put(JOB_ID, response.jobId)
        }

        if (response.jobProperties) {
            response.jobProperties.toMap()
                    .findAll { entry -> AUDIT_JOB_PROPERTIES.contains(entry.key) }
                    .each { entry ->
                        if (entry.value instanceof String) {
                            valueMap.put(entry.key, entry.value as String)
                        } else if (entry.value instanceof Calendar) {
                            valueMap.put(entry.key, entry.value as Calendar)
                        }
                    }
        }

        if (response.exceptionStackTrace) {
            valueMap.put(EXCEPTION_STACK_TRACE, response.exceptionStackTrace)

            if (response.output) {
                valueMap.put(OUTPUT, response.output)
            }
        } else {
            if (response.result) {
                valueMap.put(RESULT, response.result)
            }

            if (response.output) {
                valueMap.put(OUTPUT, response.output)
            }

            valueMap.put(RUNNING_TIME, response.runningTime)
        }

        resourceResolver.commit()
    }

    private String getAuditNodePath(String userId) {
        configurationService.displayAllAuditRecords ? AUDIT_PATH : "$AUDIT_PATH/$userId"
    }

    private List<AuditRecord> findAllAuditRecords(ResourceResolver resourceResolver, String auditNodePath) {
        def auditRecords = []

        def auditResource = resourceResolver.getResource(auditNodePath)

        if (auditResource) {
            auditResource.listChildren().each { resource ->
                if (resource.name.startsWith(AUDIT_RECORD_NODE_PREFIX)) {
                    auditRecords.add(new AuditRecord(resource))
                }

                auditRecords.addAll(findAllAuditRecords(resourceResolver, resource.path))
            }
        }

        auditRecords
    }

    private static List<AuditRecord> getAuditRecordsForDateRange(List<AuditRecord> auditRecords, Calendar startDate, Calendar endDate) {
        auditRecords.findAll { auditRecord ->
            def auditRecordDate = auditRecord.date

            auditRecordDate.set(Calendar.HOUR_OF_DAY, 0)
            auditRecordDate.set(Calendar.MINUTE, 0)
            auditRecordDate.set(Calendar.SECOND, 0)
            auditRecordDate.set(Calendar.MILLISECOND, 0)

            !auditRecordDate.before(startDate) && !auditRecordDate.after(endDate)
        }
    }

    private <T> T withResourceResolver(Closure<T> closure) {
        resourceResolverFactory.getServiceResourceResolver(null).withCloseable(closure)
    }
}
