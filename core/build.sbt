libraryDependencies ++= Seq(
  "com.typesafe.akka"         %%  "akka-actor"                  % "2.5.6",
  "com.typesafe.akka"         %%  "akka-stream"                 % "2.5.6",
  "com.typesafe.akka"         %%  "akka-slf4j"                  % "2.5.6",
  "com.typesafe.akka"         %%  "akka-http"                   % "10.0.11",
  "com.typesafe.akka"         %%  "akka-http-core"              % "10.0.11",
  "org.scalatest"             %%  "scalatest"                   % "3.0.4"      % "test",
  "org.scalaj"                %%  "scalaj-http"                 % "2.3.0",
  "org.mockito"               %   "mockito-core"                % "2.12.0",
  "org.json4s"                %%  "json4s-jackson"              % "3.6.4"
    exclude("org.slf4j", "slf4j-log4j12"),
  "org.scala-graph"           %%  "graph-core"                  % "1.12.1",
  "org.scala-graph"           %%  "graph-json"                  % "1.12.1",
  "ch.qos.logback"            %   "logback-classic"             % "1.2.3",
  "nl.grons"                  %%  "metrics4-scala"              % "4.0.1",
  "io.dropwizard.metrics"     %   "metrics-jvm"                 % "4.0.2",
  "org.scala-lang"            %   "scala-reflect"               % scalaVersion.value,
  "com.typesafe.scala-logging"%%  "scala-logging"               % "3.9.2",
  "org.apache.commons"        %   "commons-lang3"               % "3.9"
)
