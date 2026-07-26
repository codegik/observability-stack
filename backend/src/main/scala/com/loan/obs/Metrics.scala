package com.loan.obs

import zio.*
import zio.metrics.connectors.MetricsConfig
import zio.metrics.connectors.prometheus.{PrometheusPublisher, prometheusLayer}
import zio.metrics.jvm.DefaultJvmMetrics

object Metrics:
  private val metricsConfig: ULayer[MetricsConfig] =
    ZLayer.succeed(MetricsConfig(5.seconds))

  val layer: ZLayer[Any, Nothing, PrometheusPublisher] =
    metricsConfig >>> prometheusLayer

  val jvmMetricsLayer: ZLayer[Any, Throwable, Unit] =
    Runtime.enableRuntimeMetrics >>> DefaultJvmMetrics.live.unit
