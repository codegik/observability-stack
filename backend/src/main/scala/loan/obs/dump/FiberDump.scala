package loan.obs.dump

import zio.*

object FiberDump:
  def text: UIO[String] =
    Fiber.dumpAll.flatMap { dumps =>
      ZIO.foreach(dumps.toList)(_.prettyPrint).map(_.mkString("\n\n"))
    }
