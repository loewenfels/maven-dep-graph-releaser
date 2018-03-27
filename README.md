[![Download](https://api.bintray.com/packages/loewenfels/oss/dep-graph-releaser/images/download.svg) ](https://bintray.com/loewenfels/oss/dep-graph-releaser/_latestVersion)
[![Apache license](https://img.shields.io/badge/license-Apache%202.0-brightgreen.svg)](http://opensource.org/licenses/Apache2.0)
[![Build Status](https://travis-ci.org/loewenfels/dep-graph-releaser.svg?tag=v0.1.2)](https://travis-ci.org/loewenfels/dep-graph-releaser/branches)
[![Coverage](https://codecov.io/github/loewenfels/dep-graph-releaser/coverage.svg?tag=v0.1.2)](https://codecov.io/github/loewenfels/dep-graph-releaser?tag=v0.1.2)

# Dependent Graph Releaser
Dependent Graph Releaser is a tool which helps you with releasing a project and its dependent projects.

It will start of with supporting only maven projects and requires Jenkins integration.
More information will follow...

You can also use it to get an HTML which represents a Pipeline showing you, how you would need to release the projects 
manually. It generates kind of a bottom up dependency graph, or in other words a dependents graph. 

# Local usage

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

#Jenkins
The following guide shows how you can integrate dep-graph-releaser with Jenkins.

1. Get the latest resources at [bintray](https://dl.bintray.com/loewenfels/oss/ch/loewenfels/dep-graph-releaser-runner/)
   1. click on the latest version (or chose the one you want to use)
   2. download the ...-resources.jar
   3. extract it and delete the folder META-INF
2. Connect to your jenkins master (e.g., via ssh)
   1. change to the directory JENKINS_HOME/userContent (typically `cd /home/jenkins/userContent`)
   2. `mkdir dep-graph-releaser`
3. Copy the extracted resources to JENKINS_HOME/userContent/dep-graph-releaser
   1. Visit http(s)://JENKINS_URL/userContent/dep-graph-releaser/pipeline.html
   2. Most probably you will only see `Loading...` and nothing happens. That is due 
      [security measurements](https://wiki.jenkins.io/display/JENKINS/Configuring+Content+Security+Policy), see next step
4. If Loading failed in the previous step (which is the default behaviour) 
   then you need to modify the following system property: `hudson.model.DirectoryBrowserSupport.CSP`
   1. Open up the Jenkins Script Console and run: 
      ```groovy
      System.setProperty("hudson.model.DirectoryBrowserSupport.CSP", "sandbox allow-scripts; script-src 'self'");
      ```
      This will allow that scripts of the same domain (same protocol and port) can be executed.
   2. The `Loading...` should now disappear (force reload if it does not, could also be caching)
   3. Modify the JENKINS_JAVA_OPTIONS in the Jenkins config (typically at `/etc/sysconfig/jenkins`) and add the system property.
      For instance, you could modify it as follows:
      ```
      # Original before modification for dep-graph-releaser
      # JENKINS_JAVA_OPTIONS="-Djava.awt.headless=true -server -Xmx2g"
      #
      # Modified version to allow dep-graph-releaser to execute its javascripts
      JENKINS_JAVA_OPTIONS="-Djava.awt.headless=true -server -Xmx2g -Dhudson.model.DirectoryBrowserSupport.CSP=sandbox allow-scripts; script-src 'self'"
      ```
   
                

# License
Dependent Graph Releaser is published under [Apache 2.0](http://opensource.org/licenses/Apache2.0). 
