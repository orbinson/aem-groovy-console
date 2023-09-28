# Execution

There are a various way to run Groovy scripts with the AEM Groovy Console. 

## Batch script execution

Saved scripts can be remotely executed by sending a POST request to the console servlet with either the `scriptPath`
or `scriptPaths` query parameter.

### Single script

```shell
curl -d "scriptPath=/conf/groovyconsole/scripts/samples/JcrSearch.groovy" -X POST -u admin:admin http://localhost:4502/bin/groovyconsole/post.json
```

### Multiple scripts

```shell
curl -d "scriptPaths=/conf/groovyconsole/scripts/samples/JcrSearch.groovy&scriptPaths=/conf/groovyconsole/scripts/samples/FulltextQuery.groovy" -X POST -u admin:admin http://localhost:4502/bin/groovyconsole/post.json
```

## Scheduler

The Scheduler allows for immediate (asynchronous) or Cron-based script execution. Scripts are executed
as [Sling Jobs](https://sling.apache.org/documentation/bundles/apache-sling-eventing-and-job-handling.html) and are
audited in the same manner as scripts executed in the console.

### Scheduled Job Event Handling

Bundles may implement services
extending `be.orbinson.aem.groovy.console.job.event.AbstractGroovyConsoleScheduledJobEventHandler` to provide
additional post-processing or notifications for completed Groovy Console jobs.
See `be.orbinson.aem.groovy.console.job.event.DefaultGroovyConsoleEmailNotificationEventHandler` for an example of the
required annotations to register a custom event handler.
