name := "Nautilus-Cloud-Metering"
version := "0.1"

scalaVersion := "2.12.11"

val akkaHttpVersion = "10.1.8"
val circeVersion = "0.11.0"
val alpakkaVersion = "1.1.2"
val chroniclerVersion = "0.6.4"

libraryDependencies ++= Seq(
  "com.lightbend.akka"           %% "akka-stream-alpakka-unix-domain-socket" % alpakkaVersion,
  "com.lightbend.akka"           %% "akka-stream-alpakka-influxdb"           % alpakkaVersion,
  "com.github.fsanaulla"         %% "chronicler-core-shared"                 % chroniclerVersion,
  "com.github.fsanaulla"         %% "chronicler-akka-io"                     % chroniclerVersion,
  "com.github.fsanaulla"         %% "chronicler-akka-management"             % chroniclerVersion,
  "com.github.fsanaulla"         %% "chronicler-macros"                      % chroniclerVersion,
  "com.github.pureconfig"        %% "pureconfig"                             % "0.12.2",
  "com.typesafe.akka"            %% "akka-http"                              % akkaHttpVersion exclude ("com.typesafe", "config"),
  "ch.qos.logback"               % "logback-classic"                         % "1.2.3",
  "com.typesafe.scala-logging"   %% "scala-logging"                          % "3.9.2",
  "io.circe"                     %% "circe-core"                             % circeVersion,
  "io.circe"                     %% "circe-parser"                           % circeVersion,
  "io.circe"                     %% "circe-generic"                          % circeVersion,
  "de.heikoseeberger"            %% "akka-http-circe"                        % "1.27.0" exclude ("com.typesafe.akka", "akka-http"),
  "org.scalatest"                %% "scalatest"                              % "3.0.4" % Test,
  "com.typesafe.akka"            %% "akka-testkit"                           % "2.5.23" % Test,
  "com.kohlschutter.junixsocket" % "junixsocket-core"                        % "2.3.1" % Test
)

assemblyMergeStrategy in assembly := {
  case "META-INF/io.netty.versions.properties" => MergeStrategy.first
  case x =>
    val oldStrategy = (assemblyMergeStrategy in assembly).value
    oldStrategy(x)
}

assemblyJarName in assembly := s"metering-agent-${version.value}.jar"
