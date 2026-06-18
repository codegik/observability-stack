ThisBuild / scalaVersion := "3.7.2"
ThisBuild / organization := "loan"

val zioVersion       = "2.1.20"
val zioHttpVersion   = "3.4.0"
val zioLoggingVersion = "2.5.0"
val zioTelemetryVersion = "3.1.7"
val otelVersion      = "1.54.0"

lazy val backend = (project in file("."))
  .settings(
    name := "loan-backend",
    libraryDependencies ++= Seq(
      "dev.zio"        %% "zio"                  % zioVersion,
      "dev.zio"        %% "zio-streams"          % zioVersion,
      "dev.zio"        %% "zio-http"             % zioHttpVersion,
      "dev.zio"        %% "zio-jdbc"             % "0.1.2",
      "dev.zio"        %% "zio-json"             % "0.7.3",
      "dev.zio"        %% "zio-logging"          % zioLoggingVersion,
      "dev.zio"        %% "zio-opentelemetry"    % zioTelemetryVersion,
      "io.opentelemetry" % "opentelemetry-api"   % otelVersion,
      "org.postgresql" %  "postgresql"           % "42.7.4",
      "dev.zio"        %% "zio-test"             % zioVersion % Test,
      "dev.zio"        %% "zio-test-sbt"         % zioVersion % Test
    ),
    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"),
    assembly / assemblyMergeStrategy := {
      case PathList("META-INF", _*) => MergeStrategy.discard
      case "module-info.class"      => MergeStrategy.discard
      case x =>
        val old = (assembly / assemblyMergeStrategy).value
        old(x)
    }
  )
