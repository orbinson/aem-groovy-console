// Migration script for MigrationIndexAuditIT: its query filters on a non-indexed property, so it must traverse and
// is therefore flagged as needing an index when the migration runs with measureIndexUsage=true.
sql2Query("SELECT * FROM [nt:base] AS n WHERE ISDESCENDANTNODE(n, '/content') AND n.[migrationAuditMarker] = 'x'")
