[![Maven Central](https://img.shields.io/maven-central/v/be.orbinson.aem/aem-groovy-console)](https://search.maven.org/artifact/be.orbinson.aem/aem-groovy-console-all)
[![GitHub](https://img.shields.io/github/v/release/orbinson/aem-groovy-console)](https://github.com/orbinson/aem-groovy-console/releases)
[![Build and test for AEM 6.5](https://github.com/orbinson/aem-groovy-console/actions/workflows/build.yml/badge.svg)](https://github.com/orbinson/aem-groovy-console/actions/workflows/build.yml)
[![Build with AEM IDE](https://img.shields.io/badge/Built%20with-AEM%20IDE-orange)](https://plugins.jetbrains.com/plugin/9269-aem-ide)

# AEM Groovy Console

> Adobe Managed Services is not allowing AEM Groovy Console to be installed currently on production publish environments for security reasons. We are taking actions in order to get it accepted.

The AEM Groovy Console provides an interface for running [Groovy](https://www.groovy-lang.org) scripts in Adobe
Experience Manager. Scripts can be created to manipulate content in the JCR, call OSGi services, or execute arbitrary
code using the AEM, Sling, or JCR APIs. After installing the package in AEM (instructions below), see
the [console page](http://localhost:4502/groovyconsole) for documentation on the available bindings and methods. [Sample
scripts](ui.content/src/main/content/jcr_root/conf/groovyconsole/scripts/samples) are included in the package for reference.

![Screenshot](docs/assets/screenshot.png)

## Compatibility

AEM Groovy Console `19.0.1+` runs on Java `8` and `11` with an embedded Groovy version of `4.0.9`.

Supported versions:

* AEM On premise: `>= 6.5.10`
* AEM as a Cloud Service: `>= 2022.11`
* Sling: `>=12`

Consult the [installation](docs/installation.md) documentation how you can start using the AEM Groovy Console.

To install the AEM Groovy Console on older AEM versions check the original
project [aem-groovy-console](https://github.com/CID15/aem-groovy-console).

## Usage

There are several ways to [execute](docs/execution.md) Groovy scripts. The AEM Groovy Console also comes with a lot of [configuration](docs/configuration.md) options. If you want to extend the AEM Groovy Console consult the [extension](docs/extension.md) documentation for extension hooks, registering additional metaclasses and how to add notifications.

## Security

When executing Groovy Scripts using the AEM Groovy Console web interface or with HTTP requests all bindings and methods will run in the context of the request. This means the user used to authenticate needs to have sufficient permissions to execute the content of the scripts.

The `aem-groovy-console-service` [service user](ui.config/src/main/content/jcr_root/apps/groovyconsole-config/osgiconfig/config/org.apache.sling.jcr.repoinit.RepositoryInitializer-groovyconsole.config) is used to save scripts to the default location and to create audit records.

In order to run distributed scripts or create scheduled jobs, which is disabled by default, you need [configure](docs/configuration.md) specific user groups to allow script execution and add permissions for the `aem-groovy-console-service`.

If you need access to the repository for scheduled or distributed execution you need to configure extra permissions on the service user.

If you want to use distributed execution make sure to add replication permissions on `/conf/groovyconsole/replication` and to add extra permissions for the service user.

An example of a RepoInit script to achieve this would be

```text
set ACL for aem-groovy-console-service
    allow jcr:all,crx:replicate on /conf/groovyconsole/replication
    allow jcr:all /content
end
```

## Kudos

Kudos to [ICF Next](https://github.com/icfnext/aem-groovy-console)
and [CID 15](https://github.com/CID15/aem-groovy-console) for the initial development of the AEM Groovy Console. We
forked this plugin because the maintenance of the plugins seems to have stopped.
