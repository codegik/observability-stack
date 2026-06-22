ThisBuild / scalaVersion := "3.7.2"
ThisBuild / organization := "loan"

val zioVersion          = "2.1.26"
val zioHttpVersion      = "3.11.2"
val zioLoggingVersion   = "2.5.3"
val zioTelemetryVersion = "3.1.18"
val zioJsonVersion      = "0.9.2"
val otelVersion         = "1.54.0"

lazy val backend = (project in file("."))
  .settings(
    name := "loan-backend",
    libraryDependencies ++= Seq(
      "dev.zio"          %% "zio"               % zioVersion,
      "dev.zio"          %% "zio-streams"       % zioVersion,
      "dev.zio"          %% "zio-http"          % zioHttpVersion,
      "io.getquill"      %% "quill-jdbc-zio"    % "4.8.6",
      "dev.zio"          %% "zio-json"          % zioJsonVersion,
      "dev.zio"          %% "zio-logging"       % zioLoggingVersion,
      "dev.zio"          %% "zio-opentelemetry" % zioTelemetryVersion,
      "io.opentelemetry" %  "opentelemetry-api" % otelVersion,
      "org.postgresql"   %  "postgresql"        % "42.7.4",
      "dev.zio"          %% "zio-test"          % zioVersion % Test,
      "dev.zio"          %% "zio-test-sbt"      % zioVersion % Test
    ),
    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"),
    libraryDependencySchemes += "dev.zio" %% "zio-json" % "always",
    assembly / mainClass := Some("com.loan.Main"),
    assembly / assemblyMergeStrategy := {
      case PathList("META-INF", _*)         => MergeStrategy.discard
      case "module-info.class"              => MergeStrategy.discard
      case x if x.endsWith("unroll.tasty")  => MergeStrategy.first
      case x if x.endsWith("unroll.class")  => MergeStrategy.first
      case x =>
        val old = (assembly / assemblyMergeStrategy).value
        old(x)
    }
  )
