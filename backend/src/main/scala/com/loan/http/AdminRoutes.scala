package com.loan.http

import zio.*
import zio.http.*
import zio.json.*
import com.loan.obs.dump.{ThreadDump, FiberDump, CaptureService}

object AdminRoutes:
  private def captured(o: Option[String]): Response =
    Response.text(s"captured=${o.getOrElse("suppressed-by-cooldown")}")

  val routes: Routes[Any, Response] = Routes(
    Method.GET / "admin" / "health"  -> handler(Response.text("ok")),
    Method.GET / "admin" / "threads" -> handler(ThreadDump.text.map(Response.text(_))),
    Method.GET / "admin" / "fibers"  -> handler(FiberDump.text.map(Response.text(_))),

    Method.GET / "admin" / "captures" -> handler(CaptureService.list.map(l => Response.json(l.toJson))),
    Method.GET / "admin" / "captures" / string("id") -> handler { (id: String, _: Request) =>
      CaptureService.get(id).map {
        case Some(json) => Response.json(json)
        case None       => Response.notFound("no such capture")
      }
    },
    Method.POST / "admin" / "jfr" / "dump" -> handler(CaptureService.maybeCapture("MANUAL", "manual").map(captured)),

    Method.POST / "admin" / "fault" / "slow" -> handler(
      (ZIO.sleep(2.seconds) *> CaptureService.maybeCapture("LATENCY", "latencyMs=2000")).map(captured)
    ),
    Method.POST / "admin" / "fault" / "error" -> handler(
      CaptureService.maybeCapture("ERROR", "status=500").map(captured)
    ),
    Method.POST / "admin" / "fault" / "stuck" -> handler(
      (ZIO.sleep(60.seconds).forkDaemon *> CaptureService.maybeCapture("STUCK_FIBER", "forced")).map(captured)
    ),
    Method.POST / "admin" / "fault" / "pressure" -> handler(
      (ZIO.foreachParDiscard(1 to 100)(_ => ZIO.sleep(30.seconds)).forkDaemon *>
        CaptureService.maybeCapture("RUNTIME_PRESSURE", "forced")).map(captured)
    )
  )
