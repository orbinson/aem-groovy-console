# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [19.0.2] - 2023-02-21

### Fixed

-   Table component did not work anymore as expected  [#41](https://github.com/orbinson/aem-groovy-console/issues/41)

## [19.0.1] - 2023-02-17

### Changed

-   Compile with JDK 8 as to support AEM 6.5 customers who haven't updated yet

## [19.0.0] - 2023-02-15

### Changed

-   Add invalidate method, replace current invalidate with name delete: [#37](https://github.com/orbinson/aem-groovy-console/pull/37)

## [18.0.3] - 2023-01-05

### Changed

-   Only internal changes to automate releases

## [18.0.2] - 2023-01-05

### Changed

-   Split the bundle into an API and Core bundle and add package versions: [#31](https://github.com/orbinson/aem-groovy-console/issues/31)
-   Use the bnd-baseline-maven-plugin to verify when to upgrade the package versions: [#31](https://github.com/orbinson/aem-groovy-console/issues/31)

## [18.0.1] - 2023-01-04

### Changed

-   Allow to select scripts in any directory: [#29](https://github.com/orbinson/aem-groovy-console/issues/29)

### Fixed

-   Publish java docs to github pages: [#19](https://github.com/orbinson/aem-groovy-console/issues/19)
-   Add MetaClassExtensions extensions: [#32](https://github.com/orbinson/aem-groovy-console/issues/32)

## [18.0.0] - 2022-01-30

### Added

-   Merged [aem-groovy-extension](https://github.com/icfnext/aem-groovy-extension) into this project: [#10](https://github.com/orbinson/aem-groovy-console/pull/10)
-   Execute scripts on all publish instances from author environment: [#1](https://github.com/orbinson/aem-groovy-console/pull/1)
-   Add xpathQuery helper method: [#1](https://github.com/orbinson/aem-groovy-console/pull/1)
-   Add sql2Query helper method: [#24](https://github.com/orbinson/aem-groovy-console/pull/24)

### Changed

-   Enhance project structure for cloud readiness: [#1](https://github.com/orbinson/aem-groovy-console/pull/1)
-   Minimum supported version AEM 6.5.10: [#11](https://github.com/orbinson/aem-groovy-console/pull/11)
-   Update documentation links: [#18](https://github.com/orbinson/aem-groovy-console/pull/18)

### Fixed

-   Fix datatables error dialog: [#9](https://github.com/orbinson/aem-groovy-console/pull/9)
-   Service user mapping is created automatically for bundle: [#1](https://github.com/orbinson/aem-groovy-console/pull/1)

## [17.0.0] - 2021-01-12

-   Last version released by [CID15](https://github.com/CID15/aem-groovy-console)

[Unreleased]: https://github.com/orbinson/aem-groovy-console/compare/19.0.2...HEAD

[19.0.2]: https://github.com/orbinson/aem-groovy-console/compare/19.0.1...19.0.2

[19.0.1]: https://github.com/orbinson/aem-groovy-console/compare/19.0.0...19.0.1

[19.0.0]: https://github.com/orbinson/aem-groovy-console/compare/18.0.3...19.0.0

[18.0.3]: https://github.com/orbinson/aem-groovy-console/compare/18.0.2...18.0.3

[18.0.2]: https://github.com/orbinson/aem-groovy-console/compare/18.0.1...18.0.2

[18.0.1]: https://github.com/orbinson/aem-groovy-console/compare/18.0.0...18.0.1

[18.0.0]: https://github.com/orbinson/aem-groovy-console/compare/17.0.0...18.0.0

[17.0.0]: https://github.com/orbinson/aem-groovy-console/releases/tag/17.0.0
