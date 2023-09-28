# Configuration

To check the OSGi configuration navigate to
the [Groovy Console Configuration Service](http://localhost:4502/system/console/configMgr/be.orbinson.aem.groovy.console.configuration.impl.DefaultConfigurationService)
OSGi configuration page.

The following configuration properties are available:

| Property                        | Description                                                                                                                       | Default Value |
|---------------------------------|-----------------------------------------------------------------------------------------------------------------------------------|---------------|
| Email Enabled?                  | Check to enable email notification on completion of script execution.                                                             | `false`       |
| Email Recipients                | Email addresses to receive notification.                                                                                          | `[]`          |
| Script Execution Allowed Groups | List of group names that are authorized to use the console.  By default, only the 'admin' user has permission to execute scripts. | `[]`          |
| Scheduled Jobs Allowed Groups   | List of group names that are authorized to schedule jobs.  By default, only the 'admin' user has permission to schedule jobs.     | `[]`          |
| Audit Disabled?                 | Disables auditing of script execution history.                                                                                    | `false`       |
| Display All Audit Records?      | If enabled, all audit records (including records for other users) will be displayed in the console history.                       | `false`       |
| Thread Timeout                  | Time in seconds that scripts are allowed to execute before being interrupted.  If 0, no timeout is enforced.                      | 0             |
| Distributed execution enabled?  | If enabled, a script will be able to be replicated from an author and executed on all default replication agents.                 | `false`       |
