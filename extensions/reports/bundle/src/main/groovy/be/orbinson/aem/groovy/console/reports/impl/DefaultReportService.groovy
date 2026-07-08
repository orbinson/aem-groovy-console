package be.orbinson.aem.groovy.console.reports.impl

import be.orbinson.aem.groovy.console.reports.ReportException
import be.orbinson.aem.groovy.console.reports.ReportService
import be.orbinson.aem.groovy.console.reports.model.ReportDefinition
import be.orbinson.aem.groovy.console.reports.model.ReportParameter
import be.orbinson.aem.groovy.console.reports.model.ReportParameterType
import groovy.util.logging.Slf4j
import org.apache.jackrabbit.JcrConstants
import org.apache.sling.api.resource.ModifiableValueMap
import org.apache.sling.api.resource.PersistenceException
import org.apache.sling.api.resource.Resource
import org.apache.sling.api.resource.ResourceResolver
import org.apache.sling.api.resource.ResourceUtil
import org.apache.sling.jcr.resource.api.JcrResourceConstants
import org.osgi.service.component.annotations.Component

import javax.jcr.Session

import static be.orbinson.aem.groovy.console.reports.constants.ReportsConstants.CHARSET
import static be.orbinson.aem.groovy.console.reports.constants.ReportsConstants.PARAMETERS_NODE_NAME
import static be.orbinson.aem.groovy.console.reports.constants.ReportsConstants.PATH_REPORTS_FOLDER
import static be.orbinson.aem.groovy.console.reports.constants.ReportsConstants.RESOURCE_TYPE_DEFINITION

/**
 * Report definition CRUD backed by the requesting user's resolver, so JCR access control alone governs who can
 * view, run, create, edit and delete reports (see {@link ReportService}).
 */
@Component(service = ReportService, immediate = true)
@Slf4j("LOG")
class DefaultReportService implements ReportService {

    private static final String VALID_NAME_PATTERN = /[\w-]+/

    // not in org.apache.jackrabbit.JcrConstants on all platforms (e.g. AEM 6.5 uber-jar)
    private static final String PROPERTY_TITLE = "jcr:title"

    private static final String PROPERTY_DESCRIPTION = "description"

    private static final String PROPERTY_CATEGORY = "category"

    private static final String SCRIPT_FILE_SUFFIX = ".groovy"

    private static final String SCRIPT_MIME_TYPE = "application/x-groovy"

    private static final String PROPERTY_PAGE_SIZE = "pageSize"

    private static final String PROPERTY_CREATED_BY = "createdBy"

    private static final String PROPERTY_LAST_MODIFIED = "lastModified"

    private static final String PROPERTY_LAST_MODIFIED_BY = "lastModifiedBy"

    @Override
    List<ReportDefinition> getReports(ResourceResolver resourceResolver) {
        def reportsFolder = resourceResolver.getResource(PATH_REPORTS_FOLDER)

        if (reportsFolder) {
            reportsFolder.listChildren()
                    .findAll { resource -> isReportDefinition(resource) }
                    .collect { resource -> toReportDefinition(resource) }
                    .sort { reportDefinition -> (reportDefinition.title ?: reportDefinition.name).toLowerCase() }
        } else {
            []
        }
    }

    @Override
    ReportDefinition getReport(ResourceResolver resourceResolver, String name) {
        def resource = getReportResource(resourceResolver, name)

        resource ? toReportDefinition(resource) : null
    }

    @Override
    ReportDefinition saveReport(ResourceResolver resourceResolver, ReportDefinition reportDefinition, String userId) {
        validate(reportDefinition)

        try {
            def reportsFolder = ResourceUtil.getOrCreateResource(resourceResolver, PATH_REPORTS_FOLDER,
                    JcrResourceConstants.NT_SLING_FOLDER, JcrResourceConstants.NT_SLING_FOLDER, true)

            def resource = reportsFolder.getChild(reportDefinition.name)

            if (!resource) {
                resource = resourceResolver.create(reportsFolder, reportDefinition.name, [
                        (JcrConstants.JCR_PRIMARYTYPE)                     : JcrConstants.NT_UNSTRUCTURED,
                        (JcrResourceConstants.SLING_RESOURCE_TYPE_PROPERTY): RESOURCE_TYPE_DEFINITION,
                        (JcrConstants.JCR_CREATED)                         : Calendar.instance,
                        (PROPERTY_CREATED_BY)                              : userId
                ] as Map<String, Object>)
            }

            def valueMap = resource.adaptTo(ModifiableValueMap)

            valueMap.put(PROPERTY_TITLE, reportDefinition.title ?: reportDefinition.name)
            putOrRemove(valueMap, PROPERTY_DESCRIPTION, reportDefinition.description)
            putOrRemove(valueMap, PROPERTY_CATEGORY, reportDefinition.category)
            putOrRemove(valueMap, PROPERTY_PAGE_SIZE, reportDefinition.pageSize)
            valueMap.put(PROPERTY_LAST_MODIFIED, Calendar.instance)
            valueMap.put(PROPERTY_LAST_MODIFIED_BY, userId)

            saveScript(resourceResolver, resource, reportDefinition.script)
            saveParameters(resourceResolver, resource, reportDefinition.parameters)

            resourceResolver.commit()

            LOG.info("saved report definition : {} by user : {}", reportDefinition.name, userId)

            toReportDefinition(resource)
        } catch (PersistenceException e) {
            LOG.error("error saving report definition : {}", reportDefinition.name, e)

            throw new ReportException("Error saving report definition: ${reportDefinition.name}", e)
        }
    }

    @Override
    void deleteReport(ResourceResolver resourceResolver, String name) {
        def resource = getReportResource(resourceResolver, name)

        if (!resource) {
            throw new IllegalArgumentException("Report not found: $name")
        }

        try {
            resourceResolver.delete(resource)
            resourceResolver.commit()

            LOG.info("deleted report definition : {}", name)
        } catch (PersistenceException e) {
            LOG.error("error deleting report definition : {}", name, e)

            throw new ReportException("Error deleting report definition: $name", e)
        }
    }

    @Override
    boolean canCreate(ResourceResolver resourceResolver) {
        def session = resourceResolver.adaptTo(Session)

        if (session == null) {
            return false
        }

        try {
            // existing folder: need to add a child; missing folder: need to create it under the parent
            session.nodeExists(PATH_REPORTS_FOLDER) ?
                    session.hasPermission(PATH_REPORTS_FOLDER + "/x", Session.ACTION_ADD_NODE) :
                    session.hasPermission(PATH_REPORTS_FOLDER, Session.ACTION_ADD_NODE)
        } catch (Exception e) {
            LOG.debug("error checking create permission", e)

            false
        }
    }

    @Override
    boolean canEdit(ResourceResolver resourceResolver, String name) {
        if (!name?.matches(VALID_NAME_PATTERN)) {
            return false
        }

        def session = resourceResolver.adaptTo(Session)
        def path = "$PATH_REPORTS_FOLDER/$name"

        if (session == null || !session.nodeExists(path)) {
            return false
        }

        try {
            session.hasPermission(path, Session.ACTION_SET_PROPERTY)
        } catch (Exception e) {
            LOG.debug("error checking edit permission for {}", name, e)

            false
        }
    }

    // internals

    private static void validate(ReportDefinition reportDefinition) {
        if (!reportDefinition.name?.matches(VALID_NAME_PATTERN)) {
            throw new IllegalArgumentException(
                    "Report name is required and may only contain letters, digits, '-' and '_'.")
        }

        if (!reportDefinition.script?.trim()) {
            throw new IllegalArgumentException("A script is required.")
        }

        reportDefinition.parameters.each { parameter ->
            if (!parameter.name?.matches(VALID_NAME_PATTERN)) {
                throw new IllegalArgumentException(
                        "Parameter names are required and may only contain letters, digits, '-' and '_'.")
            }
        }
    }

    private static void putOrRemove(ModifiableValueMap valueMap, String name, Object value) {
        if (value != null) {
            valueMap.put(name, value)
        } else {
            valueMap.remove(name)
        }
    }

    private static void saveScript(ResourceResolver resourceResolver, Resource reportResource, String script) {
        // replace any existing script file (defensive; the file name tracks the report node name)
        reportResource.listChildren().findAll { child -> isScriptFile(child) }.each { child ->
            resourceResolver.delete(child)
        }

        def fileResource = resourceResolver.create(reportResource, "${reportResource.name}$SCRIPT_FILE_SUFFIX",
                [(JcrConstants.JCR_PRIMARYTYPE): JcrConstants.NT_FILE] as Map<String, Object>)

        resourceResolver.create(fileResource, JcrConstants.JCR_CONTENT, [
                (JcrConstants.JCR_PRIMARYTYPE) : JcrConstants.NT_RESOURCE,
                (JcrConstants.JCR_MIMETYPE)    : SCRIPT_MIME_TYPE,
                (JcrConstants.JCR_ENCODING)    : CHARSET,
                (JcrConstants.JCR_LASTMODIFIED): Calendar.instance,
                (JcrConstants.JCR_DATA)        : new ByteArrayInputStream(script.getBytes(CHARSET))
        ] as Map<String, Object>)
    }

    private static void saveParameters(ResourceResolver resourceResolver, Resource reportResource,
                                       List<ReportParameter> parameters) {
        def parametersResource = reportResource.getChild(PARAMETERS_NODE_NAME)

        if (parametersResource) {
            resourceResolver.delete(parametersResource)
        }

        if (parameters) {
            parametersResource = resourceResolver.create(reportResource, PARAMETERS_NODE_NAME,
                    [(JcrConstants.JCR_PRIMARYTYPE): JcrConstants.NT_UNSTRUCTURED] as Map<String, Object>)

            parameters.eachWithIndex { parameter, index ->
                def properties = [
                        (JcrConstants.JCR_PRIMARYTYPE): JcrConstants.NT_UNSTRUCTURED,
                        "name"                        : parameter.name,
                        "label"                       : parameter.label ?: parameter.name,
                        "type"                        : (parameter.type ?: ReportParameterType.STRING).name(),
                        "required"                    : parameter.required,
                        "order"                       : parameter.order ?: index
                ] as Map<String, Object>

                if (parameter.defaultValue != null) {
                    properties["defaultValue"] = parameter.defaultValue
                }

                if (parameter.options) {
                    properties["options"] = parameter.options as String[]
                }

                if (parameter.type == ReportParameterType.PATH) {
                    properties["pathType"] = parameter.pathType ?: "NODE"

                    if (parameter.rootPath) {
                        properties["rootPath"] = parameter.rootPath
                    }
                }

                resourceResolver.create(parametersResource, parameter.name, properties)
            }
        }
    }

    private static boolean isReportDefinition(Resource resource) {
        resource.isResourceType(RESOURCE_TYPE_DEFINITION)
    }

    private static boolean isScriptFile(Resource resource) {
        resource.name.endsWith(SCRIPT_FILE_SUFFIX)
    }

    private static String readScript(Resource reportResource) {
        def scriptFile = reportResource.listChildren().find { child -> isScriptFile(child) }

        def stream = scriptFile?.getChild(JcrConstants.JCR_CONTENT)
                ?.valueMap
                ?.get(JcrConstants.JCR_DATA, InputStream)

        stream?.withCloseable { it.getText(CHARSET) }
    }

    private static Resource getReportResource(ResourceResolver resourceResolver, String name) {
        if (!name?.matches(VALID_NAME_PATTERN)) {
            return null
        }

        def resource = resourceResolver.getResource("$PATH_REPORTS_FOLDER/$name")

        resource && isReportDefinition(resource) ? resource : null
    }

    private static ReportDefinition toReportDefinition(Resource resource) {
        def properties = resource.valueMap

        new ReportDefinition(
                name: resource.name,
                title: properties.get(PROPERTY_TITLE, resource.name),
                description: properties.get(PROPERTY_DESCRIPTION, String),
                category: properties.get(PROPERTY_CATEGORY, String),
                script: readScript(resource),
                pageSize: properties.get(PROPERTY_PAGE_SIZE, Integer),
                parameters: toParameters(resource),
                created: properties.get(JcrConstants.JCR_CREATED, Calendar),
                createdBy: properties.get(PROPERTY_CREATED_BY, String),
                lastModified: properties.get(PROPERTY_LAST_MODIFIED, Calendar),
                lastModifiedBy: properties.get(PROPERTY_LAST_MODIFIED_BY, String)
        )
    }

    private static List<ReportParameter> toParameters(Resource reportResource) {
        def parametersResource = reportResource.getChild(PARAMETERS_NODE_NAME)

        if (!parametersResource) {
            return []
        }

        parametersResource.listChildren().collect { resource ->
            def properties = resource.valueMap

            new ReportParameter(
                    name: properties.get("name", resource.name),
                    label: properties.get("label", resource.name),
                    type: toParameterType(properties.get("type", String)),
                    defaultValue: properties.get("defaultValue", String),
                    required: properties.get("required", false),
                    options: (properties.get("options", new String[0]) as List).findAll(),
                    pathType: properties.get("pathType", "NODE"),
                    rootPath: properties.get("rootPath", String),
                    order: properties.get("order", 0)
            )
        }.sort { parameter -> parameter.order }
    }

    private static ReportParameterType toParameterType(String type) {
        try {
            type ? ReportParameterType.valueOf(type.toUpperCase()) : ReportParameterType.STRING
        } catch (IllegalArgumentException ignored) {
            ReportParameterType.STRING
        }
    }
}
