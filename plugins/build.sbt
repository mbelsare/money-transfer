libraryDependencies ++= Seq(
  "org.scalatest"                             %% "scalatest"                          % "3.0.4"        % "test",
  "org.scalaj"                                %% "scalaj-http"                        % "2.3.0",
  "commons-io"                                %  "commons-io"                         % "2.5",
  "org.scala-lang.modules"                    %% "scala-parser-combinators"           % "1.0.6",
  "org.json4s"                                %% "json4s-native"                      % "3.5.2"
    exclude("org.slf4j", "slf4j-log4j12"),
  "ch.qos.logback"                            % "logback-classic"                     % "1.2.3",
  "com.typesafe.scala-logging"                %% "scala-logging"                      % "3.9.2",
  "org.mockito"                               %  "mockito-core"                       % "2.12.0"
)
