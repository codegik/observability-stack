package loan.http

import zio.*
import zio.http.*

object AppRoutes:
  val routes: Routes[Any, Response] = Routes(
    Method.GET / "api" / "health" -> handler(Response.text("ok"))
  ) @@ HeaderMiddleware.middleware
