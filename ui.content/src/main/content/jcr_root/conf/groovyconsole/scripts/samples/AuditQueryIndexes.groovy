/*
 * Demonstrates the query-audit extension on any Sling or AEM instance.
 *
 * Run this with "Run with query audit" (the arrow next to the Run button, available when the
 * query-audit extension is installed). The Query audit tab then shows, per JCR query the script
 * executed, whether an Oak index on THIS instance covers it:
 *
 *  - the jcr:uuid lookup is answered by the built-in /oak:index/uuid property index -> "indexed"
 *  - no index covers the arbitrary demoMarker property, so both filters on it (one JCR-SQL2,
 *    one XPath) force Oak to traverse every node under /content -> "needs index"
 */

def queryManager = session.workspace.queryManager

def indexed = queryManager.createQuery(
    "SELECT * FROM [mix:referenceable] AS node WHERE node.[jcr:uuid] = '00000000-0000-0000-0000-000000000000'",
    "JCR-SQL2").execute()

println "uuid lookup returned ${indexed.nodes.size()} node(s)"

def unindexed = queryManager.createQuery(
    "SELECT * FROM [nt:base] AS node WHERE ISDESCENDANTNODE(node, '/content') AND node.[demoMarker] = 'audit-me'",
    "JCR-SQL2").execute()

println "demoMarker filter (JCR-SQL2) returned ${unindexed.nodes.size()} node(s)"

def xpath = queryManager.createQuery(
    "/jcr:root/content//*[@demoMarker = 'audit-me']",
    "xpath").execute()

println "demoMarker filter (XPath) returned ${xpath.nodes.size()} node(s)"
