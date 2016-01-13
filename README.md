# Saul Project 
[![Build Status](http://128.174.241.91:8080/buildStatus/icon?job=saul)](http://128.174.241.91:8080/job/saul)

[![Travis Build Status](https://magnum.travis-ci.com/IllinoisCogComp/saul.svg?token=sh2TUxymJtwGcwzpH5oQ&branch=master)](https://magnum.travis-ci.com/IllinoisCogComp/saul)
 
The project contains two modules. See the readme files for each module: 

- Saul-Core: [The README of the core of the Saul project](saul-core/README.md)  
- Saul-Examples: [The README of the examples package](saul-examples/README.md)

The project's [official chat group is at Slack](https://cogcomp.slack.com/messages/saul/)

## Usage 

First, run `sbt`. 

- `projects` will show the names of the existing module names. 
    - `project saulCore` will take you inside the core package. 
    -  `project saulExamples` will take you inside the examples package.
    - `project saulWebapp` will take you inside the webapp package. Then type `run` to start the server. Type `localhost:9000` in browser address bar to see the webapp.
- Inside each project you can `compile` it, or `run` it. 
- To fix the formatting problems, run `format`
