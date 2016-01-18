
val cogcompNLPVersion = "3.0.14"
val cogcompPipelineVersion = "0.1.15"


lazy val root = (project in file(".")).
  aggregate(saulCore, saulExamples)
 forcegc := false
//
lazy val commonSettings = Seq(
  organization := "edu.illinois.cs.cogcomp",
  name := "saul-project",
  version := "0.1",
  scalaVersion := "2.11.7",
  resolvers ++= Seq(
    Resolver.mavenLocal,
    "CogcompSoftware" at "http://cogcomp.cs.illinois.edu/m2repo/"
  ),
  javaOptions ++= List("-Xmx8g"),
  libraryDependencies ++= Seq(
    "edu.illinois.cs.cogcomp" % "illinois-core-utilities" % cogcompNLPVersion exclude("org.slf4j", "slf4j-log4j12") withSources,
    "com.gurobi" % "gurobi" % "6.0",
    "org.apache.commons" % "commons-math3" % "3.0",
    "org.scalatest" % "scalatest_2.11" % "2.2.4"
  ),
  fork in run := true,
  publishTo := Some(Resolver.sftp("CogcompSoftwareRepo", "bilbo.cs.illinois.edu", "/mounts/bilbo/disks/0/www/cogcomp/html/m2repo/"))
)

lazy val saulCore = (project in file("saul-core")).
  settings(commonSettings: _*).
  settings(
    name := "saul",
    libraryDependencies ++= Seq(
      "edu.illinois.cs.cogcomp" % "LBJava" % "1.2.2",
      "com.typesafe.play" % "play_2.11" % "2.4.3" exclude("ch.qos.logback", "logback-classic")
    )
  )

lazy val saulExamples = (project in file("saul-examples")).
  settings(commonSettings: _*).
  settings(
      name := "saul-examples",
      javaOptions += "-Xmx6g",
      mainClass in assembly := Some("edu.illinois.cs.cogcomp.saulexamples.nlp.SemanticRoleLabeling.pipeline_App"),
     assemblyJarName in assembly := "aTr.jar",
     assemblyMergeStrategy in assembly := {
      case PathList("javax", "servlet", xs @ _*) => MergeStrategy.first
      case PathList("META-INF", xs @ _*) => MergeStrategy.discard
      //case x => MergeStrategy.first
      case x if x.contains("org.slf4j") => MergeStrategy.last
      case PathList(ps @ _*) if ps.last endsWith ".html" => MergeStrategy.first
      case "application.conf"                            => MergeStrategy.concat
      case "unwanted.txt"                                => MergeStrategy.discard
      case x => MergeStrategy.first
      // val oldStrategy = (assemblyMergeStrategy in assembly).value
      //oldStrategy(x)
    },
      libraryDependencies ++= Seq(
      "edu.illinois.cs.cogcomp" % "illinois-nlp-pipeline" % cogcompPipelineVersion,
      "edu.illinois.cs.cogcomp" % "illinois-curator" % cogcompNLPVersion,
      "edu.illinois.cs.cogcomp" % "illinois-edison" % cogcompNLPVersion,
      "edu.illinois.cs.cogcomp" % "illinois-srl"  % "5.1.7",
      "edu.illinois.cs.cogcomp" % "illinois-nlp-readers" % "0.0.2-SNAPSHOT"
    )
  ).dependsOn(saulCore).aggregate(saulCore)

lazy val saulWebapp = (project in file("saul-webapp")).
  enablePlugins(PlayScala).
  settings(commonSettings: _*).
  settings(
    name := "saul-webapp",
    libraryDependencies ++= Seq(
      "org.webjars" %% "webjars-play" % "2.4.0-1",
      "org.webjars" % "bootstrap" % "3.1.1-2",
      jdbc,
      cache,
      ws,
      specs2 % Test
    ),
    resolvers += "scalaz-bintray" at "http://dl.bintray.com/scalaz/releases",
    routesGenerator := InjectedRoutesGenerator
  ).dependsOn(saulCore).aggregate(saulCore)

