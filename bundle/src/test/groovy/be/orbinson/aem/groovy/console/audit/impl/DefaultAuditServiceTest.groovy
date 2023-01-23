package be.orbinson.aem.groovy.console.audit.impl

import be.orbinson.aem.groovy.console.api.context.impl.RequestScriptContext
import be.orbinson.aem.groovy.console.audit.AuditRecord
import be.orbinson.aem.groovy.console.audit.AuditService
import be.orbinson.aem.groovy.console.configuration.impl.DefaultConfigurationService
import be.orbinson.aem.groovy.console.response.impl.DefaultRunScriptResponse
import io.wcm.testing.mock.aem.junit5.AemContext
import io.wcm.testing.mock.aem.junit5.AemContextExtension
import org.apache.commons.lang3.exception.ExceptionUtils
import org.apache.sling.testing.mock.sling.ResourceResolverType
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

import static org.junit.jupiter.api.Assertions.assertEquals
import static org.junit.jupiter.api.Assertions.assertNotNull

@ExtendWith(AemContextExtension.class)
class DefaultAuditServiceTest {

    private final AemContext context = new AemContext();

    AuditService auditService;

    @BeforeEach
    void beforeEach() {
        context.build().resource("/var/groovyconsole").commit();
        context.registerInjectActivateService(new DefaultConfigurationService())
        auditService = context.registerInjectActivateService(new DefaultAuditService())
    }

    @Test
    void createAuditRecordForScriptWithResultAndOutput() {
        def request = context.request()
        def response = context.response()

        def script = "script content"
        def output = "output"
        def runningTime = "running time"
        def result = "result"

        def scriptContext = new RequestScriptContext(request, response, new ByteArrayOutputStream(), null, script)

        def runScriptResponse = DefaultRunScriptResponse.fromResult(scriptContext, result, output, runningTime)

        def auditRecord = auditService.createAuditRecord(runScriptResponse)

        assertNotNull(context.resourceResolver().getResource(auditRecord.path))

        assertEquals(auditRecord.script, script)
        assertEquals(auditRecord.result, result)
        assertEquals(auditRecord.output, output)
    }

    @Test
    void createAuditRecordWithException() {
        def request = context.request()
        def response = context.response()

        def scriptContext = new RequestScriptContext(request, response, new ByteArrayOutputStream(), null, "script content")

        def exception = new RuntimeException("")

        def runScriptResponse = DefaultRunScriptResponse.fromException(scriptContext, "output", exception)

        def auditRecord = auditService.createAuditRecord(runScriptResponse)

        assertNotNull(context.resourceResolver().getResource(auditRecord.path))

        assertEquals(auditRecord.script, "script content")
        assertEquals(auditRecord.output, "output")
        assertEquals(auditRecord.exceptionStackTrace, ExceptionUtils.getStackTrace(exception))

    }

    @Test
    void createMultipleAuditRecords() {
        def request = context.request()
        def response = context.response()

        def scriptContext = new RequestScriptContext(request, response, new ByteArrayOutputStream(), null, "script content")

        def runScriptResponse = DefaultRunScriptResponse.fromResult(scriptContext, "result", "output", "running time")

        def auditRecords = []

        (1..5).each {
            auditRecords.add(auditService.createAuditRecord(runScriptResponse))
        }

        assertAuditRecordsCreated(auditRecords)
    }

    @Test
    void getAuditRecordsForValidDateRange() {
        def request = context.request()
        def response = context.response()

        def scriptContext = new RequestScriptContext(request, response, new ByteArrayOutputStream(), null, "script content")

        def runScriptResponse = DefaultRunScriptResponse.fromResult(scriptContext, "result", "output", "running time")

        auditService.createAuditRecord(runScriptResponse)

        def tests = [
                [startDateOffset: -2, endDateOffset: -1, size: 0],
                [startDateOffset: -1, endDateOffset: 0, size: 1],
                [startDateOffset: 0, endDateOffset: 0, size: 1],
                [startDateOffset: 0, endDateOffset: 1, size: 1],
                [startDateOffset: -1, endDateOffset: 1, size: 1],
                [startDateOffset: 1, endDateOffset: 2, size: 0]
        ]
        tests.each { map ->
            def startDate = getDate(map.startDateOffset)
            def endDate = getDate(map.endDateOffset)
            assertEquals(map.size, auditService.getAuditRecords(context.resourceResolver().userID, startDate, endDate).size())
        }
    }

    private Calendar getDate(Integer offset) {
        def date = (new Date() + offset).toCalendar()

        date.set(Calendar.HOUR_OF_DAY, 0)
        date.set(Calendar.MINUTE, 0)
        date.set(Calendar.SECOND, 0)
        date.set(Calendar.MILLISECOND, 0)

        date
    }

    private void assertAuditRecordsCreated(List<AuditRecord> auditRecords) {
        auditRecords.each {
            assertNotNull(context.resourceResolver().getResource(it.path))
        }
    }
}
