package be.orbinson.aem.groovy.console.reports.impl

import be.orbinson.aem.groovy.console.reports.ReportResultStore
import be.orbinson.aem.groovy.console.reports.data.ReportData
import be.orbinson.aem.groovy.console.reports.model.ReportResultPage
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import groovy.util.logging.Slf4j
import org.apache.jackrabbit.JcrConstants
import org.apache.sling.api.resource.Resource
import org.apache.sling.api.resource.ResourceResolver
import org.apache.sling.api.resource.ResourceResolverFactory
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

import static be.orbinson.aem.groovy.console.reports.constants.ReportsConstants.CHARSET
import static be.orbinson.aem.groovy.console.reports.constants.ReportsConstants.RESULT_NODE_NAME

@Component(service = ReportResultStore, immediate = true)
@Slf4j("LOG")
class DefaultReportResultStore implements ReportResultStore {

    @Reference
    private ResourceResolverFactory resourceResolverFactory

    @Reference
    private ReportsConfigurationService configurationService

    @Override
    void save(ResourceResolver resourceResolver, Resource executionResource, ReportData reportData) {
        def byteStream = new ByteArrayOutputStream()

        new GZIPOutputStream(byteStream).withCloseable { gzipStream ->
            gzipStream.write(JsonOutput.toJson(reportData.toMap()).getBytes(CHARSET))
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

    @Override
    ReportResultPage getPage(String executionId, int page, int pageSize) {
        def reportData = getData(executionId)

        if (reportData == null) {
            return null
        }

        def effectivePageSize = pageSize > 0 ? pageSize : configurationService.defaultPageSize
        def totalRows = reportData.rows.size()
        def totalPages = totalRows ? (int) Math.ceil(totalRows / (double) effectivePageSize) : 0
        def effectivePage = Math.min(Math.max(page, 1), Math.max(totalPages, 1))

        def fromIndex = (effectivePage - 1) * effectivePageSize
        def toIndex = Math.min(fromIndex + effectivePageSize, totalRows)

        new ReportResultPage(
                columns: reportData.columns,
                rows: fromIndex < toIndex ? reportData.rows.subList(fromIndex, toIndex) : [],
                page: effectivePage,
                pageSize: effectivePageSize,
                totalRows: totalRows,
                totalPages: totalPages
        )
    }

    @Override
    ReportData getData(String executionId) {
        withResourceResolver { ResourceResolver resourceResolver ->
            def executionResource = DefaultReportExecutionService.getExecutionResource(resourceResolver,
                    executionId)

            def contentResource = executionResource
                    ?.getChild(RESULT_NODE_NAME)
                    ?.getChild(JcrConstants.JCR_CONTENT)

            def stream = contentResource?.valueMap?.get(JcrConstants.JCR_DATA, InputStream)

            if (!stream) {
                return null
            }

            def json = new GZIPInputStream(stream).withCloseable { gzipStream ->
                new JsonSlurper().parse(gzipStream)
            }

            ResultParser.fromReportDataMap(json as Map)
        }
    }

    // internals

    private <T> T withResourceResolver(Closure<T> closure) {
        resourceResolverFactory.getServiceResourceResolver(null).withCloseable(closure)
    }
}
