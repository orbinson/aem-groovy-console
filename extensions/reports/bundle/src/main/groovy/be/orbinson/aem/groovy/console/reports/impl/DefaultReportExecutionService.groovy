package be.orbinson.aem.groovy.console.reports.impl

import be.orbinson.aem.groovy.console.GroovyConsoleService
import be.orbinson.aem.groovy.console.reports.ReportException
import be.orbinson.aem.groovy.console.reports.ReportExecutionService
import be.orbinson.aem.groovy.console.reports.data.ReportData
import be.orbinson.aem.groovy.console.reports.model.ReportDefinition
import be.orbinson.aem.groovy.console.reports.model.ReportExecution
import be.orbinson.aem.groovy.console.reports.model.ReportExecutionStatus
import be.orbinson.aem.groovy.console.reports.model.ReportPreview
import be.orbinson.aem.groovy.console.response.RunScriptResponse
import be.orbinson.aem.groovy.console.streaming.ExecutionCallback
import be.orbinson.aem.groovy.console.streaming.ExecutionRegistry
import groovy.json.JsonBuilder
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import groovy.transform.Synchronized
import groovy.util.logging.Slf4j
import org.apache.jackrabbit.JcrConstants
import org.apache.sling.api.resource.ModifiableValueMap
import org.apache.sling.api.resource.Resource
import org.apache.sling.api.resource.ResourceResolver
import org.apache.sling.api.resource.ResourceResolverFactory
import org.apache.sling.api.resource.ResourceUtil
import org.apache.sling.jcr.resource.api.JcrResourceConstants
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

import java.util.regex.Pattern
import java.util.zip.GZIPOutputStream

import static be.orbinson.aem.groovy.console.reports.constants.ReportsConstants.*

@Component(service = ReportExecutionService, immediate = true)
@Slf4j("LOG")
class DefaultReportExecutionService implements ReportExecutionService {

    private static final Pattern EXECUTION_ID_PATTERN = Pattern.compile(/[\w-]+(\/[\w-]+)*/)

    private static final String DATE_FORMAT_YEAR = "yyyy"

    private static final String DATE_FORMAT_MONTH = "MM"

    private static final String DATE_FORMAT_DAY = "dd"

    private static final String PROPERTY_STATUS = "status"

    private static final String PROPERTY_REPORT_NAME = "reportName"

    private static final String PROPERTY_USER_ID = "userId"

    private static final String PROPERTY_STARTED_AT = "startedAt"

    private static final String PROPERTY_FINISHED_AT = "finishedAt"

    private static final String PROPERTY_DURATION_MILLIS = "durationMillis"

    private static final String PROPERTY_RUNNING_TIME = "runningTime"

    private static final String PROPERTY_ROW_COUNT = "rowCount"

    private static final String PROPERTY_COLUMN_COUNT = "columnCount"

    private static final String PROPERTY_TRUNCATED = "truncated"

    private static final String PROPERTY_PARAMETER_VALUES = "parameterValues"

    private static final String PROPERTY_OUTPUT = "output"

    private static final String PROPERTY_EXCEPTION_STACK_TRACE = "exceptionStackTrace"

    private static final String PROPERTY_SCRIPT = "script"

    @Reference
    private GroovyConsoleService groovyConsoleService

    @Reference
    private ExecutionRegistry executionRegistry

    @Reference
    private ResourceResolverFactory resourceResolverFactory

    @Reference
    private ReportsConfigurationService configurationService

    @Override
    ReportExecution execute(ReportDefinition reportDefinition, Map<String, String> parameterValues,
                            ResourceResolver resourceResolver) {
        def coercedValues = ParameterCoercer.coerce(reportDefinition.parameters, parameterValues)
        def script = resolveScript(reportDefinition)
        def userId = resourceResolver.userID

        def executionId = createExecution(reportDefinition.name, userId, coercedValues, script)

        LOG.info("executing report : {} as user : {}, execution ID : {}", reportDefinition.name, userId,
                executionId)

        // run asynchronously so a long report doesn't block the request thread (and hit HTTP timeouts);
        // the script is resolved up-front with the user's resolver, then executed with a detached clone
        // that the registry closes when the run finishes. The execution is persisted as RUNNING and the
        // callback flips it to SUCCESS/FAILED once complete; clients poll the execution for the outcome.
        def asyncResolver = resourceResolver.clone(null)
        def scriptContext = buildReportContext(reportDefinition.name, coercedValues, script, userId, asyncResolver)

        executionRegistry.start(scriptContext, { RunScriptResponse response, long durationMillis ->
            if (response.exceptionStackTrace) {
                finishFailed(executionId, response, durationMillis)
            } else {
                finishSuccess(executionId, response, durationMillis)
            }
        } as ExecutionCallback)

        getExecution(executionId)
    }

    @Override
    ReportPreview preview(ReportDefinition reportDefinition, Map<String, String> parameterValues,
                          ResourceResolver resourceResolver) {
        def coercedValues = ParameterCoercer.coerce(reportDefinition.parameters, parameterValues)
        def script = resolveScript(reportDefinition)
        def userId = resourceResolver.userID

        LOG.info("previewing report : {} as user : {}", reportDefinition.name, userId)

        def response = runReport(reportDefinition.name, coercedValues, script, userId, resourceResolver).response
        def output = response.output ? truncate(response.output) : null

        if (response.exceptionStackTrace) {
            return new ReportPreview(
                    status: ReportExecutionStatus.FAILED,
                    exceptionStackTrace: response.exceptionStackTrace,
                    output: output,
                    runningTime: response.runningTime)
        }

        def reportData = ResultParser.parse(response.result)
        def truncated = capRows(reportData)

        new ReportPreview(
                status: ReportExecutionStatus.SUCCESS,
                data: reportData,
                truncated: truncated,
                output: output,
                runningTime: response.runningTime)
    }

    // synchronous run used by preview() (the try-out); execute() runs asynchronously via the registry
    private Map runReport(String reportName, Map<String, Object> coercedValues, String script, String userId,
                          ResourceResolver resourceResolver) {
        def scriptContext = buildReportContext(reportName, coercedValues, script, userId, resourceResolver)

        def start = System.currentTimeMillis()
        def response = groovyConsoleService.runScript(scriptContext)

        [response: response, durationMillis: System.currentTimeMillis() - start]
    }

    private static DefaultReportScriptContext buildReportContext(String reportName, Map<String, Object> coercedValues,
                                                                 String script, String userId,
                                                                 ResourceResolver resourceResolver) {
        def outputStream = new ByteArrayOutputStream()

        new DefaultReportScriptContext(
                reportName: reportName,
                parameterValues: coercedValues,
                resourceResolver: resourceResolver,
                outputStream: outputStream,
                printStream: new PrintStream(outputStream, true, CHARSET),
                script: script,
                userId: userId
        )
    }

    // cap rows to the configured maximum; returns true when the result was truncated
    private boolean capRows(ReportData reportData) {
        def maxResultRows = configurationService.maxResultRows

        if (maxResultRows > 0 && reportData.rows.size() > maxResultRows) {
            reportData.rows = reportData.rows.subList(0, maxResultRows)

            return true
        }

        false
    }

    @Override
    List<ReportExecution> getExecutions(String reportName) {
        if (!isValidExecutionId(reportName)) {
            return []
        }

        withResourceResolver { ResourceResolver resourceResolver ->
            def reportFolder = resourceResolver.getResource("$PATH_EXECUTIONS_FOLDER/$reportName")

            def executions = []

            if (reportFolder) {
                collectExecutions(reportFolder, executions)
            }

            executions.sort { execution -> -(execution.startedAt?.timeInMillis ?: 0) }
        }
    }

    @Override
    ReportExecution getExecution(String executionId) {
        withResourceResolver { ResourceResolver resourceResolver ->
            def resource = getExecutionResource(resourceResolver, executionId)

            resource ? toExecution(resource) : null
        }
    }

    @Override
    @Synchronized
    void deleteExecution(String executionId) {
        withResourceResolver { ResourceResolver resourceResolver ->
            def resource = getExecutionResource(resourceResolver, executionId)

            if (!resource) {
                throw new IllegalArgumentException("Execution not found: $executionId")
            }

            resourceResolver.delete(resource)
            resourceResolver.commit()

            LOG.info("deleted execution : {}", executionId)
        }
    }

    // internals

    static boolean isValidExecutionId(String executionId) {
        executionId && EXECUTION_ID_PATTERN.matcher(executionId).matches()
    }

    static Resource getExecutionResource(ResourceResolver resourceResolver, String executionId) {
        if (!isValidExecutionId(executionId)) {
            return null
        }

        def resource = resourceResolver.getResource("$PATH_EXECUTIONS_FOLDER/$executionId")

        resource && resource.name.startsWith(EXECUTION_NODE_PREFIX) ? resource : null
    }

    static ReportExecution toExecution(Resource resource) {
        def properties = resource.valueMap

        new ReportExecution(
                id: resource.path.substring(PATH_EXECUTIONS_FOLDER.length() + 1),
                reportName: properties.get(PROPERTY_REPORT_NAME, String),
                status: toStatus(properties.get(PROPERTY_STATUS, String)),
                userId: properties.get(PROPERTY_USER_ID, String),
                startedAt: properties.get(PROPERTY_STARTED_AT, Calendar),
                finishedAt: properties.get(PROPERTY_FINISHED_AT, Calendar),
                durationMillis: properties.get(PROPERTY_DURATION_MILLIS, Long),
                runningTime: properties.get(PROPERTY_RUNNING_TIME, String),
                rowCount: properties.get(PROPERTY_ROW_COUNT, Long),
                columnCount: properties.get(PROPERTY_COLUMN_COUNT, Long),
                truncated: properties.get(PROPERTY_TRUNCATED, false),
                parameterValues: toParameterValues(properties.get(PROPERTY_PARAMETER_VALUES, String)),
                output: properties.get(PROPERTY_OUTPUT, String),
                exceptionStackTrace: properties.get(PROPERTY_EXCEPTION_STACK_TRACE, String)
        )
    }

    private static String resolveScript(ReportDefinition reportDefinition) {
        if (!reportDefinition.script?.trim()) {
            throw new ReportException("Report ${reportDefinition.name} has no script")
        }

        reportDefinition.script
    }

    @Synchronized
    private String createExecution(String reportName, String userId, Map<String, Object> parameterValues,
                                   String script) {
        withResourceResolver { ResourceResolver resourceResolver ->
            def date = Calendar.instance
            def folderPath = new StringBuilder(PATH_EXECUTIONS_FOLDER)
                    .append("/").append(reportName)
                    .append("/").append(date.format(DATE_FORMAT_YEAR))
                    .append("/").append(date.format(DATE_FORMAT_MONTH))
                    .append("/").append(date.format(DATE_FORMAT_DAY))
                    .toString()

            def folderResource = ResourceUtil.getOrCreateResource(resourceResolver, folderPath,
                    JcrResourceConstants.NT_SLING_FOLDER, JcrResourceConstants.NT_SLING_FOLDER, true)

            def executionName = ResourceUtil.createUniqueChildName(folderResource, EXECUTION_NODE_PREFIX)

            def executionResource = resourceResolver.create(folderResource, executionName, [
                    (JcrConstants.JCR_PRIMARYTYPE): JcrConstants.NT_UNSTRUCTURED,
                    (PROPERTY_STATUS)             : ReportExecutionStatus.RUNNING.name(),
                    (PROPERTY_REPORT_NAME)        : reportName,
                    (PROPERTY_USER_ID)            : userId,
                    (PROPERTY_STARTED_AT)         : date,
                    (PROPERTY_PARAMETER_VALUES)   : new JsonBuilder(parameterValues).toString(),
                    (PROPERTY_SCRIPT)             : script
            ] as Map<String, Object>)

            resourceResolver.commit()

            executionResource.path.substring(PATH_EXECUTIONS_FOLDER.length() + 1)
        }
    }

    private void finishSuccess(String executionId, RunScriptResponse response, long durationMillis) {
        def reportData = ResultParser.parse(response.result)
        def truncated = capRows(reportData)

        if (truncated) {
            LOG.warn("result truncated to {} rows for execution : {}", configurationService.maxResultRows, executionId)
        }

        withResourceResolver { ResourceResolver resourceResolver ->
            def resource = getExecutionResource(resourceResolver, executionId)
            def valueMap = resource.adaptTo(ModifiableValueMap)

            valueMap.put(PROPERTY_STATUS, ReportExecutionStatus.SUCCESS.name())
            valueMap.put(PROPERTY_FINISHED_AT, Calendar.instance)
            valueMap.put(PROPERTY_DURATION_MILLIS, durationMillis)
            valueMap.put(PROPERTY_RUNNING_TIME, response.runningTime ?: "")
            valueMap.put(PROPERTY_ROW_COUNT, reportData.rows.size() as long)
            valueMap.put(PROPERTY_COLUMN_COUNT, reportData.columns.size() as long)
            valueMap.put(PROPERTY_TRUNCATED, truncated)

            if (response.output) {
                valueMap.put(PROPERTY_OUTPUT, truncate(response.output))
            }

            saveResult(resourceResolver, resource, reportData)

            resourceResolver.commit()
        }
    }

    private void finishFailed(String executionId, RunScriptResponse response, long durationMillis) {
        withResourceResolver { ResourceResolver resourceResolver ->
            def resource = getExecutionResource(resourceResolver, executionId)
            def valueMap = resource.adaptTo(ModifiableValueMap)

            valueMap.put(PROPERTY_STATUS, ReportExecutionStatus.FAILED.name())
            valueMap.put(PROPERTY_FINISHED_AT, Calendar.instance)
            valueMap.put(PROPERTY_DURATION_MILLIS, durationMillis)
            valueMap.put(PROPERTY_EXCEPTION_STACK_TRACE, response.exceptionStackTrace)

            if (response.output) {
                valueMap.put(PROPERTY_OUTPUT, truncate(response.output))
            }

            resourceResolver.commit()
        }
    }

    private static void saveResult(ResourceResolver resourceResolver, Resource executionResource,
                                   ReportData reportData) {
        def json = JsonOutput.toJson(reportData.toMap())

        def byteStream = new ByteArrayOutputStream()

        new GZIPOutputStream(byteStream).withCloseable { gzipStream ->
            gzipStream.write(json.getBytes(CHARSET))
        }

        def fileResource = resourceResolver.create(executionResource, RESULT_NODE_NAME,
                [(JcrConstants.JCR_PRIMARYTYPE): JcrConstants.NT_FILE] as Map<String, Object>)

        resourceResolver.create(fileResource, JcrConstants.JCR_CONTENT, [
                (JcrConstants.JCR_PRIMARYTYPE): JcrConstants.NT_RESOURCE,
                (JcrConstants.JCR_MIMETYPE)   : "application/json",
                (JcrConstants.JCR_ENCODING)   : CHARSET,
                (JcrConstants.JCR_DATA)       : new ByteArrayInputStream(byteStream.toByteArray())
        ] as Map<String, Object>)
    }

    private String truncate(String output) {
        def maxOutputLength = configurationService.maxOutputLength

        maxOutputLength > 0 && output.length() > maxOutputLength ? output.substring(0, maxOutputLength) : output
    }

    private static void collectExecutions(Resource resource, List<ReportExecution> executions) {
        resource.listChildren().each { child ->
            if (child.name.startsWith(EXECUTION_NODE_PREFIX)) {
                executions.add(toExecution(child))
            } else {
                collectExecutions(child, executions)
            }
        }
    }

    private static Map<String, Object> toParameterValues(String json) {
        if (!json) {
            return [:]
        }

        try {
            new JsonSlurper().parseText(json) as Map<String, Object>
        } catch (Exception ignored) {
            [:]
        }
    }

    private static ReportExecutionStatus toStatus(String status) {
        try {
            status ? ReportExecutionStatus.valueOf(status) : null
        } catch (IllegalArgumentException ignored) {
            null
        }
    }

    private <T> T withResourceResolver(Closure<T> closure) {
        resourceResolverFactory.getServiceResourceResolver(null).withCloseable(closure)
    }
}
