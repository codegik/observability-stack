package loan.http

import zio.*
import zio.http.*
import loan.context.ContextRefs

object HeaderMiddleware:
  val CorrelationHeader = "X-Correlation-Id"
  val UserHeader        = "X-User-Id"

  val middleware: Middleware[Any] = new Middleware[Any]:
    def apply[Env1 <: Any, Err](routes: Routes[Env1, Err]): Routes[Env1, Err] =
      routes.transform[Env1] { h =>
        handler { (req: Request) =>
          val cid = req.rawHeader(CorrelationHeader).getOrElse("")
          val uid = req.rawHeader(UserHeader).getOrElse("")
          if cid.isEmpty || uid.isEmpty then
            ZIO.succeed(Response.badRequest(s"missing required headers $CorrelationHeader and $UserHeader"))
          else
            ContextRefs.correlationId.locally(cid) {
              ContextRefs.userId.locally(uid) {
                ZIO.logAnnotate("correlation_id", cid) {
                  ZIO.logAnnotate("user_id", uid) {
                    h(req)
                  }
                }
              }
            }
        }
      }
