// Static reference content ported from the classic UI's methods.html and builders.html

export interface MethodDoc {
  signature: string;
  description: string;
}

export const METHODS: MethodDoc[] = [
  { signature: 'getPage(String path)', description: 'Get the Page for the given path, or null if it does not exist.' },
  {
    signature: 'getNode(String path)',
    description: 'Get the Node for the given path. Throws javax.jcr.RepositoryException if it does not exist.',
  },
  {
    signature: 'getResource(String path)',
    description: 'Get the Resource for the given path, or null if it does not exist.',
  },
  {
    signature: 'getModel(String path, Class type)',
    description: 'Get an instance of a Sling Model class for the Resource at the given path.',
  },
  {
    signature: 'getService(Class<ServiceType> serviceType)',
    description: 'Get the OSGi service instance for the given type.',
  },
  { signature: 'getService(String className)', description: 'Get the OSGi service instance for the given class name.' },
  {
    signature: 'getServices(Class<ServiceType> serviceType, String filter)',
    description: 'Get OSGi services for the given type and filter expression.',
  },
  {
    signature: 'getServices(String className, String filter)',
    description: 'Get OSGi services for the given class name and filter expression.',
  },
  {
    signature: 'copy "sourceAbsolutePath" to "destinationAbsolutePath"',
    description: 'Groovy DSL syntax for copying a node, equivalent to session.workspace.copy(source, destination).',
  },
  {
    signature: 'move "sourceAbsolutePath" to "destinationAbsolutePath"',
    description:
      'Groovy DSL syntax for moving a node, equivalent to session.move(source, destination); the session is saved automatically.',
  },
  {
    signature: 'rename [node] to "newName"',
    description:
      'Groovy DSL syntax for renaming a node; the renamed node retains its order and the session is saved automatically.',
  },
  { signature: 'save()', description: 'Save the current JCR session.' },
  { signature: 'activate(String path)', description: 'Activate the node at the given path.' },
  {
    signature: 'activate(String path, ReplicationOptions options)',
    description: 'Activate the node at the given path with supplied options.',
  },
  { signature: 'deactivate(String path)', description: 'Deactivate the node at the given path.' },
  {
    signature: 'deactivate(String path, ReplicationOptions options)',
    description: 'Deactivate the node at the given path with supplied options.',
  },
  { signature: 'delete(String path)', description: 'Delete the node at the given path.' },
  {
    signature: 'delete(String path, ReplicationOptions options)',
    description: 'Delete the node at the given path with supplied options.',
  },
  {
    signature: 'distribute(String path, String agentId = "publish", boolean isDeep = "false")',
    description: 'Distribute the node at the given path with supplied options. (Only applicable on AEMaaCS)',
  },
  {
    signature: 'invalidate(String path, String agentId = "publish", boolean isDeep = "false")',
    description: 'Invalidate the node at the given path with supplied options. (Only applicable on AEMaaCS)',
  },
  {
    signature: 'createQuery(Map predicates)',
    description: 'Create a Query instance from the QueryBuilder for the current JCR session.',
  },
  {
    signature: 'xpathQuery(String query)',
    description: 'Execute an XPath query using the QueryManager for the current JCR session.',
  },
  {
    signature: 'sql2Query(String query)',
    description: 'Execute an SQL-2 query using the QueryManager for the current JCR session.',
  },
];

export const NODE_BUILDER_EXAMPLE = `nodeBuilder.etc {
    satirists("sling:Folder") {
        bierce(firstName: "Ambrose", lastName: "Bierce", birthDate: Calendar.instance.updated(year: 1842, month: 5, date: 24))
        mencken(firstName: "H.L.", lastName: "Mencken", birthDate: Calendar.instance.updated(year: 1880, month: 8, date: 12))
        other("sling:Folder", "jcr:title": "Other")
    }
}`;

export const PAGE_BUILDER_EXAMPLE = `pageBuilder.content {
    beer {
        styles("Styles") {
            "jcr:content"("jcr:lastModifiedBy": "me", "jcr:lastModified": Calendar.instance) {
                data("sling:Folder")
            }
            dubbel("Dubbel")
            tripel("Tripel")
            saison("Saison")
        }
        breweries("Breweries", "jcr:lastModifiedBy": "me", "jcr:lastModified": Calendar.instance)
    }
}`;

export const ENHANCEMENTS_DOC_URL =
  'https://orbinson.github.io/aem-groovy-console/be/orbinson/aem/groovy/console/extension/impl/metaclass/package-summary.html';
