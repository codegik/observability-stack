package loan.obs

import zio.*
import zio.logging.{consoleJsonLogger, ConsoleLoggerConfig, LogFormat}

object Logging:
  private val config =
    ConsoleLoggerConfig.default.copy(format = LogFormat.default + LogFormat.allAnnotations)

  val layer: ZLayer[Any, Any, Unit] =
    Runtime.removeDefaultLoggers >>> consoleJsonLogger(config)
