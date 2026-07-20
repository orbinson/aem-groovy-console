package be.orbinson.aem.groovy.console.reports.impl

import be.orbinson.aem.groovy.console.GroovyConsoleService
import be.orbinson.aem.groovy.console.reports.ReportDistributorRegistry
import be.orbinson.aem.groovy.console.reports.ReportException
import be.orbinson.aem.groovy.console.reports.ReportExecutionService
import be.orbinson.aem.groovy.console.reports.ReportResultStore
import be.orbinson.aem.groovy.console.reports.data.ReportData
import be.orbinson.aem.groovy.console.reports.model.ReportDefinition
import be.orbinson.aem.groovy.console.reports.model.ReportDistributionTarget
import be.orbinson.aem.groovy.console.reports.model.ReportExecution
import be.orbinson.aem.groovy.console.reports.model.ReportExecutionStatus
import be.orbinson.aem.groovy.console.reports.model.ReportPreview
import be.orbinson.aem.groovy.console.response.RunScriptResponse
import be.orbinson.aem.groovy.console.streaming.ExecutionCallback
import be.orbinson.aem.groovy.console.streaming.ExecutionRegistry
import groovy.json.JsonBuilder
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


    private static final String PROPERTY_PARAMETER_VALUES = "parameterValues"

    private static final String OUTPUT_NODE_NAME = "output"

    private static final String PROPERTY_EXCEPTION_STACK_TRACE = "exceptionStackTrace"

    private static final String PROPERTY_SCRIPT = "script"

    private static final String PROPERTY_DISTRIBUTION_ERRORS = "distributionErrors"

    @Reference
    private GroovyConsoleService groovyConsoleService

    @Reference
    private ExecutionRegistry executionRegistry

    @Reference
    private ReportResultStore resultStore

    @Reference
    private ResourceResolverFactory resourceResolverFactory

    @Reference
    private ReportsConfigurationService reportsConfigurationService

    @Reference
    private ReportDistributorRegistry distributorRegistry

    @Override
    ReportExecution execute(ReportDefinition reportDefinition, Map<String, Object> parameterValues,
                            ResourceResolver resourceResolver) {
        execute(reportDefinition, parameterValues, resourceResolver, [])
    }

    @Override
    ReportExecution execute(ReportDefinition reportDefinition, Map<String, String> parameterValues,
                            ResourceResolver resourceResolver, List<ReportDistributionTarget> distributionTargets) {
        def coercedValues = ParameterCoercer.coerce(reportDefinition.parameters, parameterValues)
        def script = resolveScript(reportDefinition)
        def userId = resourceResolver.userID

        def executionId = createExecution(reportDefinition.name, userId, coercedValues, script)

        LOG.info("executing report : {} as user : {}, execution ID : {}", reportDefinition.name, userId,
                executionId)

        def asyncResolver = resourceResolver.clone(null)
        def scriptContext = buildReportContext(reportDefinition.name, coercedValues, script, userId, asyncResolver)
        def targets = distributionTargets ?: []

        executionRegistry.start(scriptContext, { RunScriptResponse response, long durationMillis ->
            if (response.exceptionStackTrace) {
                finishFailed(executionId, response, durationMillis)
            } else {
                finishSuccess(executionId, response, durationMillis, targets)
            }
        } as ExecutionCallback)

        getExecution(executionId)
    }

    @Override
    ReportPreview preview(ReportDefinition reportDefinition, Map<String, Object> parameterValues,
                          ResourceResolver resourceResolver) {
        def coercedValues = ParameterCoercer.coerce(reportDefinition.parameters, parameterValues)
        def script = resolveScript(reportDefinition)
        def userId = resourceResolver.userID

        LOG.info("previewing report : {} as user : {}", reportDefinition.name, userId)

        // run on a clone so a preview script that makes (then fails to commit) transient JCR changes cannot
        // leave them pending on the request-scoped resolver — runScript does not revert on failure
        def response = resourceResolver.clone(null).withCloseable { previewResolver ->
            runReport(reportDefinition.name, coercedValues, script, userId, previewResolver).response
        }
        def output = response.output ?: null

        if (response.exceptionStackTrace) {
            return new ReportPreview(
                    status: ReportExecutionStatus.FAILED,
                    exceptionStackTrace: response.exceptionStackTrace,
                    output: output,
                    runningTime: response.runningTime)
        }

        def reportData = ResultParser.parse(response.result)

        new ReportPreview(
                status: ReportExecutionStatus.SUCCESS,
                data: reportData,
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

    @Override
    void distribute(String executionId, List<ReportDistributionTarget> distributionTargets) {
        def execution = getExecution(executionId)

        if (!execution) {
            throw new ReportException("execution not found: ${executionId}")
        }

        if (execution.status != ReportExecutionStatus.SUCCESS) {
            throw new ReportException("only a successful execution can be distributed: ${executionId}")
        }

        def reportData = resultStore.getData(executionId)

        if (reportData == null) {
            throw new ReportException("no stored result for execution: ${executionId}")
        }

        applyDistributions(execution, reportData, distributionTargets ?: [])
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
                parameterValues: toParameterValues(properties.get(PROPERTY_PARAMETER_VALUES, String)),
                output: readOutput(resource),
                exceptionStackTrace: properties.get(PROPERTY_EXCEPTION_STACK_TRACE, String),
                distributionErrors: (properties.get(PROPERTY_DISTRIBUTION_ERRORS, new String[0]) as List)
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

    private void finishSuccess(String executionId, RunScriptResponse response, long durationMillis,
                               List<ReportDistributionTarget> distributionTargets) {
        def reportData = ResultParser.parse(response.result)

        def distributable = withResourceResolver { ResourceResolver resourceResolver ->
            def resource = getExecutionResource(resourceResolver, executionId)

            if (!resource) {
                LOG.warn("execution {} was removed before it completed; discarding result", executionId)

                return false
            }

            def maxRows = reportsConfigurationService.maxResultRows

            if (maxRows > 0 && reportData.rows.size() > maxRows) {
                LOG.warn("execution {} produced {} rows, exceeding the configured maximum of {}; failing the run",
                        executionId, reportData.rows.size(), maxRows)

                def valueMap = resource.adaptTo(ModifiableValueMap)

                valueMap.put(PROPERTY_STATUS, ReportExecutionStatus.FAILED.name())
                valueMap.put(PROPERTY_FINISHED_AT, Calendar.instance)
                valueMap.put(PROPERTY_DURATION_MILLIS, durationMillis)
                valueMap.put(PROPERTY_EXCEPTION_STACK_TRACE,
                        "Report produced ${reportData.rows.size()} rows, exceeding the configured maximum of " +
                                "${maxRows}. Narrow the report or raise the limit." as String)

                if (response.output) {
                    saveOutput(resourceResolver, resource, response.output)
                }

                resourceResolver.commit()

                return false
            }

            def valueMap = resource.adaptTo(ModifiableValueMap)

            valueMap.put(PROPERTY_STATUS, ReportExecutionStatus.SUCCESS.name())
            valueMap.put(PROPERTY_FINISHED_AT, Calendar.instance)
            valueMap.put(PROPERTY_DURATION_MILLIS, durationMillis)
            valueMap.put(PROPERTY_RUNNING_TIME, response.runningTime ?: "")
            valueMap.put(PROPERTY_ROW_COUNT, reportData.rows.size() as long)
            valueMap.put(PROPERTY_COLUMN_COUNT, reportData.columns.size() as long)

            if (response.output) {
                saveOutput(resourceResolver, resource, response.output)
            }

            resultStore.save(resourceResolver, resource, reportData)

            resourceResolver.commit()

            true
        }

        if (distributable && distributionTargets) {
            applyDistributions(getExecution(executionId), reportData, distributionTargets)
        }
    }

    // Apply each distribution target to a successful result. Failures are recorded on the execution but never
    // fail the run — the report itself succeeded.
    private void applyDistributions(ReportExecution execution, ReportData reportData,
                                    List<ReportDistributionTarget> distributionTargets) {
        def errors = []

        distributionTargets.each { target ->
            try {
                def distributor = distributorRegistry.getDistributor(target.distributorId)

                if (!distributor) {
                    errors.add("no distributor registered for '${target.distributorId}'" as String)

                    return
                }

                distributor.distribute(execution, reportData, target)

                LOG.info("distributed execution {} via {}", execution.id, target.distributorId)
            } catch (Exception e) {
                LOG.error("distribution via {} failed for execution {}", target.distributorId, execution.id, e)

                errors.add("${target.distributorId}: ${e.message}" as String)
            }
        }

        if (errors) {
            withResourceResolver { ResourceResolver resourceResolver ->
                def resource = getExecutionResource(resourceResolver, execution.id)

                if (resource) {
                    resource.adaptTo(ModifiableValueMap).put(PROPERTY_DISTRIBUTION_ERRORS, errors as String[])

                    resourceResolver.commit()
                }
            }
        }
    }

    private void finishFailed(String executionId, RunScriptResponse response, long durationMillis) {
        withResourceResolver { ResourceResolver resourceResolver ->
            def resource = getExecutionResource(resourceResolver, executionId)

            if (!resource) {
                LOG.warn("execution {} was removed before it completed; discarding failure", executionId)

                return
            }

            def valueMap = resource.adaptTo(ModifiableValueMap)

            valueMap.put(PROPERTY_STATUS, ReportExecutionStatus.FAILED.name())
            valueMap.put(PROPERTY_FINISHED_AT, Calendar.instance)
            valueMap.put(PROPERTY_DURATION_MILLIS, durationMillis)
            valueMap.put(PROPERTY_EXCEPTION_STACK_TRACE, response.exceptionStackTrace)

            if (response.output) {
                saveOutput(resourceResolver, resource, response.output)
            }

            resourceResolver.commit()
        }
    }

    private static void saveOutput(ResourceResolver resourceResolver, Resource executionResource, String output) {
        def fileResource = resourceResolver.create(executionResource, OUTPUT_NODE_NAME,
                [(JcrConstants.JCR_PRIMARYTYPE): JcrConstants.NT_FILE] as Map<String, Object>)

        resourceResolver.create(fileResource, JcrConstants.JCR_CONTENT, [
                (JcrConstants.JCR_PRIMARYTYPE): JcrConstants.NT_RESOURCE,
                (JcrConstants.JCR_MIMETYPE)   : "text/plain",
                (JcrConstants.JCR_ENCODING)   : CHARSET,
                (JcrConstants.JCR_DATA)       : new ByteArrayInputStream(output.getBytes(CHARSET))
        ] as Map<String, Object>)
    }

    private static String readOutput(Resource resource) {
        def stream = resource.getChild(OUTPUT_NODE_NAME)
                ?.getChild(JcrConstants.JCR_CONTENT)
                ?.valueMap
                ?.get(JcrConstants.JCR_DATA, InputStream)

        stream?.withCloseable { it.getText(CHARSET) }
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
