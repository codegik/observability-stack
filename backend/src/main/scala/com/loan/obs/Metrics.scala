package com.loan.obs

import zio.*
import zio.metrics.connectors.prometheus.{PrometheusPublisher, publisherLayer}
import zio.metrics.jvm.DefaultJvmMetrics

object Metrics:
  val layer: ZLayer[Any, Nothing, PrometheusPublisher] =
    publisherLayer

  val jvmMetricsLayer: ZLayer[Any, Throwable, Unit] =
    Runtime.enableRuntimeMetrics >>> DefaultJvmMetrics.live.unit
