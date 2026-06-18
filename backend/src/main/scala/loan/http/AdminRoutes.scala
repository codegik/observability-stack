package loan.http

import zio.*
import zio.http.*
import loan.obs.dump.{ThreadDump, FiberDump}

object AdminRoutes:
  val routes: Routes[Any, Response] = Routes(
    Method.GET / "admin" / "health" -> handler(Response.text("ok")),
    Method.GET / "admin" / "threads" -> handler(ThreadDump.text.map(Response.text(_))),
    Method.GET / "admin" / "fibers"  -> handler(FiberDump.text.map(Response.text(_)))
  )
