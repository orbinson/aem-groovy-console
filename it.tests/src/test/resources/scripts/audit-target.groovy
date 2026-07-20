// Script under test for IndexDetectionIT: a plain-JCR query on a custom (un-indexed) property, so on the Sling
// Starter it must traverse until a matching property index is created.
sql2Query("SELECT * FROM [nt:base] AS n WHERE ISDESCENDANTNODE(n, '/content') AND n.[auditMarker] = 'flagged'")
