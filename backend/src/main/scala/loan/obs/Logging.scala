package loan.obs

import zio.*
import zio.logging.consoleJsonLogger

object Logging:
  val layer: ZLayer[Any, Config.Error, Unit] =
    Runtime.removeDefaultLoggers >>> consoleJsonLogger()
