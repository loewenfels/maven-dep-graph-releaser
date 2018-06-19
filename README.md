[![Download](https://api.bintray.com/packages/loewenfels/oss/dep-graph-releaser/images/download.svg) ](https://bintray.com/loewenfels/oss/dep-graph-releaser/_latestVersion)
[![Apache license](https://img.shields.io/badge/license-Apache%202.0-brightgreen.svg)](http://opensource.org/licenses/Apache2.0)
[![Build Status](https://travis-ci.org/loewenfels/dep-graph-releaser.svg?tags=v0.6.1)](https://travis-ci.org/loewenfels/dep-graph-releaser/branches)
[![Coverage](https://codecov.io/github/loewenfels/dep-graph-releaser/coverage.svg?tags=v0.6.1)](https://codecov.io/github/loewenfels/dep-graph-releaser?tags=v0.6.1)

# Dependent Graph Releaser
Dependent Graph Releaser is a tool which helps you with releasing a project and its dependent projects.
Its aim is to simplify the process of using up-to-date internal dependencies.
 
A simple example: having project `A -> B -> C` we want to automate the following process:
- release C
- update the dependency in B
- release B
- update the dependency in A
- release A

It will start of with supporting only maven projects and requires Jenkins integration.

Have a look at the [Wiki](https://github.com/loewenfels/dep-graph-releaser/wiki) 
for more information and try out the [online example](https://loewenfels.github.io/dep-graph-releaser/#./release.json).
   
# Design Decisions   
- In case a dependency has specified `${project.version}` as `<version>` then it will be replaced with the new version.
   
# Known Limitations

Outlined in the Wiki page [Known Limitations](https://github.com/loewenfels/dep-graph-releaser/wiki/Known-Limitations).

# License
Dependent Graph Releaser is published under [Apache 2.0](http://opensource.org/licenses/Apache2.0). 
