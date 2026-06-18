package loan.http

import zio.*
import zio.http.*
import java.util.concurrent.TimeUnit
import loan.context.{ContextRefs, RequestContext}
import loan.obs.dump.{CaptureService, FiberRegistry}

object HeaderMiddleware:
  val CorrelationHeader = "X-Correlation-Id"
  val UserHeader        = "X-User-Id"

  val middleware: Middleware[Any] = new Middleware[Any]:
    def apply[Env1 <: Any, Err](routes: Routes[Env1, Err]): Routes[Env1, Err] =
      routes.transform[Env1] { h =>
        Handler.fromFunctionZIO[Request] { (req: Request) =>
          val cid = req.rawHeader(CorrelationHeader).getOrElse("")
          val uid = req.rawHeader(UserHeader).getOrElse("")
          if cid.isEmpty || uid.isEmpty then
            ZIO.succeed(Response.badRequest(s"missing required headers $CorrelationHeader and $UserHeader"))
          else
            ContextRefs.correlationId.locally(cid) {
              ContextRefs.userId.locally(uid) {
                ZIO.logAnnotate("correlation_id", cid) {
                  ZIO.logAnnotate("user_id", uid) {
                    handle(h, req, cid, uid)
                  }
                }
              }
            }
        }
      }

  private def handle[Env1](h: Handler[Env1, Response, Request, Response], req: Request, cid: String, uid: String) =
    for
      fid   <- ZIO.fiberId
      key    = fid.ids.headOption.fold("unknown")(i => s"zio-fiber-$i")
      start <- Clock.currentTime(TimeUnit.MILLISECONDS)
      resp  <- ZIO.acquireReleaseWith(
                 ZIO.succeed(FiberRegistry.register(key, RequestContext(cid, uid), start))
               )(_ => ZIO.succeed(FiberRegistry.unregister(key))) { _ =>
                 for
                   r   <- ZIO.scoped[Env1](h(req))
                   end <- Clock.currentTime(TimeUnit.MILLISECONDS)
                   ms   = end - start
                   _   <- ZIO.logInfo(s"${req.method} ${req.path} status=${r.status.code} ${ms}ms")
                   _   <- CaptureService.maybeCapture("LATENCY", s"latencyMs=$ms").when(ms > CaptureService.LatencyThresholdMs)
                   _   <- CaptureService.maybeCapture("ERROR", s"status=${r.status.code}").when(r.status.code >= 500)
                 yield r
               }
    yield resp
