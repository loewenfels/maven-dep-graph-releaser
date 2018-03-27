[![Download](https://api.bintray.com/packages/loewenfels/oss/dep-graph-releaser/images/download.svg) ](https://bintray.com/loewenfels/oss/dep-graph-releaser/_latestVersion)
[![Apache license](https://img.shields.io/badge/license-Apache%202.0-brightgreen.svg)](http://opensource.org/licenses/Apache2.0)
[![Build Status](https://travis-ci.org/loewenfels/dep-graph-releaser.svg?branch=master)](https://travis-ci.org/loewenfels/dep-graph-releaser/branches)
[![Coverage](https://codecov.io/github/loewenfels/dep-graph-releaser/coverage.svg?branch=master)](https://codecov.io/github/loewenfels/dep-graph-releaser?branch=master)

# Dependent Graph Releaser
Dependent Graph Releaser is a tool which helps you with releasing a project and its dependent projects.

It will start of with supporting only maven projects and requires Jenkins integration.
More information will follow...

You can also use it to get an HTML which represents a Pipeline showing you, how you would need to release the projects 
manually. It generates kind of a bottom up dependency graph, or in other words a dependents graph. 

Add the projects you want to analyse the folder `repos` (in the project directory) and run the following gradle command:
````
gr html -Pg=your.group.id -Pa=the.artifact.id
````
This creates a `pipeline.html` in the folder `rootFolder/build/html`. 

Notice, the task is clever and does not regenerate the html if nothing has changed in the code 
(the gradle task is mainly there to ease development, 
using `repos` as input of the task takes too much time depending on the number of projects you have).
Thus, if you add another project to the `repos` folder and want to rerun the task, then call `cleanJson` first. 
Or just always call `gr cleanJson html` :wink:. 

You can use `gr server` to start a lightweight local server serving `pipeline.html`. 
This is necessary since the `pipeline.html` wants to include a javascript file and your browser forbids that to protect you from XSS attacks.  

# License
Dependent Graph Releaser is published under [Apache 2.0](http://opensource.org/licenses/Apache2.0). 
