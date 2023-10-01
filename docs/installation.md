# Installation

## Manual

1. Download the
   console [aem-groovy-console-all](https://github.com/orbinson/aem-groovy-console/releases/download/19.0.3/aem-groovy-console-all-19.0.3.zip)
   content package and install with [PackMgr](http://localhost:4502/crx/packmgr). For previous versions you can search
   on the [Maven Central repository](https://search.maven.org/search?q=a:aem-groovy-console).

2. Navigate to the [groovyconsole](http://localhost:4502/groovyconsole) page.

## Embedded package

To deploy the Groovy Console as an embedded package you need to update your `pom.xml`

1. Add the `aem-groovy-console-all` to the `<dependencies>` section

   ```xml
   <dependency>
     <groupId>be.orbinson.aem</groupId>
     <artifactId>aem-groovy-console-all</artifactId>
     <version>19.0.3</version>
     <type>zip</type>
   </dependency>
   ```
2. Embed the package in with
   the [filevault-package-maven-plugin](https://jackrabbit.apache.org/filevault-package-maven-plugin/) in
   the `<embeddeds>` section

   ```xml
   <embedded>
      <groupId>be.orbinson.aem</groupId>
      <artifactId>aem-groovy-console-all</artifactId>
      <target>/apps/vendor-packages/content/install</target>
   </embedded>
   ```

## AEM Dispatcher

If you need to have the Groovy Console available through the dispatcher on a publish instance you can add the filters
following configuration.

```text
# Allow Groovy Console page
/001 {
    /type "allow"
    /url "/groovyconsole"
}
/002 {
    /type "allow"
    /url "/apps/groovyconsole.html"
}

# Allow servlets
/003 {
    /type "allow"
    /path "/bin/groovyconsole/*"
}
```

## Building From Source

To build and install the latest development version of the Groovy Console to use in AEM (or if you've made source
modifications), run
the following Maven command.

```shell
mvn clean install -P autoInstallSinglePackage
```

### Maven profiles

Maven profiles can be used to install the bundles to AEM / Sling.

* AEM Author running on http://localhost:4502
    * api, bundle, ui.apps, ui.apps.aem, ui.config, ui.content: `-P auto-deploy`
    * all: `-P auto-deploy-single-package,aem`
* AEM Publish running on http://localhost:4503
    * api, bundle, ui.apps, ui.apps.aem, ui.config, ui.content: `-P auto-deploy,publish`
    * all: `-P auto-deploy-single-package,aem,publish`
* Sling running on http://localhost:8080
    * api, bundle, ui.apps, ui.apps.aem, ui.config, ui.content: `-P auto-deploy,sling`
    * all: `-P auto-deploy-single-package,sling`
