import sbt.Keys._
import scoverage.ScoverageKeys.coverageFailOnMinimum

name := "money-transfer"
scalastyleConfig := baseDirectory.value / "scalastyle-config.xml"

lazy val compileScalastyle = taskKey[Unit]("compileScalastyle")
lazy val testScalastyle = taskKey[Unit]("testScalastyle")
lazy val commonSettings =
  Seq(
    scalaVersion := "2.12.4",
    logLevel := Level.Info,
    crossPaths := false,
    scalacOptions := Seq("-unchecked", "-deprecation", "-feature"),
    test in assembly := {},
    offline := true,
    parallelExecution := false,
    autoAPIMappings := true,
    testScalastyle := (org.scalastyle.sbt.ScalastylePlugin.autoImport.scalastyle in Test).toTask("").value,
    test := (test in Test dependsOn testScalastyle).value,
    test := (test in Test dependsOn compileScalastyle).value,
    compileScalastyle := (org.scalastyle.sbt.ScalastylePlugin.autoImport.scalastyle in Compile).toTask("").value,
    compile := (compile in Compile dependsOn compileScalastyle).value,
    assembly := (assembly dependsOn compileScalastyle).value,
    assemblyMergeStrategy in assembly := {
      case PathList("com", "google", xs@_*) =>
        MergeStrategy.last
      case PathList("org", "aopalliance", "intercept", xs@_*) =>
        MergeStrategy.last
      case PathList("org", "aopalliance", "aop", xs@_*) =>
        MergeStrategy.last
      case PathList("mime.types") =>
        MergeStrategy.last
      case x =>
        val oldStrategy = (assemblyMergeStrategy in assembly).value
        oldStrategy(x)
    },
    coverageMinimum := 80,
    coverageFailOnMinimum := true,
    coverageHighlighting := true,
    coverageOutputHTML := true,
    coverageOutputXML := true,
    coverageEnabled in(Test, compile) := true,
    coverageEnabled in(Compile, compile) := false
  )

lazy val core = (project in file("core")).
  settings(commonSettings: _*).
  settings(
    name := "core",
    organization := "com.revolut.money_transfer"
  ).dependsOn(commons)

lazy val plugins = (project in file("plugins")).
  settings(commonSettings: _*).
  settings(
    name := "plugins",
    organization := "com.revolut.money_transfer"
  ).dependsOn(commons)

lazy val commons = (project in file("commons")).
  settings(commonSettings: _*).
  settings(
    name := "commons",
    organization := "com.revolut.money_transfer"
  )

lazy val root = (project in file("."))
  .settings(commonSettings: _*)
  .settings(
    name := "money-transfer",
    organization := "com.revolut.money_transfer",
    aggregate in doc := false
  ).dependsOn(core, plugins, commons)
  .aggregate(core, plugins, commons)

addCommandAlias("testCoverage", "; clean; coverage; test:test; coverageOff; coverageReport; coverageAggregate")
