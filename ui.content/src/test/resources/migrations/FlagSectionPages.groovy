// Migration: flag every we-retail section page for review.
// Queries by cq:template (over nt:base), so in a real repository it needs a property/lucene index on cq:template.
def statement = "SELECT * FROM [nt:base] AS content " +
        "WHERE ISDESCENDANTNODE(content, '/content/we-retail') " +
        "AND content.[cq:template] = '/conf/we-retail/settings/wcm/templates/section-page'"

sql2Query(statement).each { node ->
    node.set("reviewed", true)
}

save()
