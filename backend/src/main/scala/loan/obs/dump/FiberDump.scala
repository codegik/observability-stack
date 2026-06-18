package loan.obs.dump

import zio.*

object FiberDump:
  def text: UIO[String] =
    for
      ref <- Ref.make(Chunk.empty[String])
      _   <- Fiber.dumpAllWith(d => d.prettyPrint.flatMap(s => ref.update(_ :+ s)))
      out <- ref.get
    yield out.mkString("\n\n")
