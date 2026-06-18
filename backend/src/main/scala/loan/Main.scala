package loan

import zio.*
import zio.http.*
import loan.http.{AdminRoutes, AppRoutes}
import loan.obs.Logging

object Main extends ZIOAppDefault:
  override val bootstrap: ZLayer[Any, Any, Unit] = Logging.layer

  private val routes = AdminRoutes.routes ++ AppRoutes.routes

  def run =
    ZIO.logInfo("loan-backend starting on :8080") *>
      Server.serve(routes).provide(Server.defaultWithPort(8080))
