// Creates the Oak property index that covers IndexDetectionIT's target query (propertyNames must be of type NAME).
def oakIndex = session.getNode('/oak:index')
if (!oakIndex.hasNode('auditMarkerIt')) {
    def idx = oakIndex.addNode('auditMarkerIt', 'oak:QueryIndexDefinition')
    idx.setProperty('type', 'property')
    idx.setProperty('propertyNames', ['auditMarker'] as String[], javax.jcr.PropertyType.NAME)
    idx.setProperty('reindex', true)
    session.save()
}
return 'created'
