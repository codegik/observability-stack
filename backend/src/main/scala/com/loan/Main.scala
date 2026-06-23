package com.loan

import zio.*
import zio.http.*
import com.loan.http.{AdminRoutes, AppRoutes}
import com.loan.obs.{Logging, Telemetry}
import com.loan.db.{Database, Repositories}
import com.loan.domain.LoanService
import com.loan.obs.dump.{CaptureService, Watchers}
import zio.telemetry.opentelemetry.OpenTelemetry
import zio.telemetry.opentelemetry.tracing.Tracing

object Main extends ZIOAppDefault:
  override val bootstrap: ZLayer[Any, Any, Unit] = Logging.layer

  private val routes = AdminRoutes.routes ++ AppRoutes.routes

  private val appLayer = Database.context >>> Repositories.layer >>> LoanService.layer

  private val tracingLayer =
    (OpenTelemetry.global ++ OpenTelemetry.contextZIO) >>> OpenTelemetry.tracing("loan-backend")

  def run =
    (ZIO.serviceWith[Tracing](Telemetry.set) *>
      ZIO.logInfo("loan-backend starting on :8080") *>
      CaptureService.init *>
      Watchers.all *>
      Server.serve(routes))
      .provide(Server.defaultWithPort(8080), appLayer, tracingLayer)
