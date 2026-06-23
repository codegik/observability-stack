package com.loan.obs

import zio.telemetry.opentelemetry.tracing.Tracing

object Telemetry:
  @volatile private var ref: Option[Tracing] = None
  def set(t: Tracing): Unit = ref = Some(t)
  def get: Option[Tracing] = ref
