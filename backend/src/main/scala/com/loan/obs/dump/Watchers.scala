package com.loan.obs.dump

import zio.*
import java.util.concurrent.TimeUnit
import java.lang.management.ManagementFactory

object Watchers:
  val stuckFiber: UIO[Unit] =
    (for
      now   <- Clock.currentTime(TimeUnit.MILLISECONDS)
      stuck  = FiberRegistry.olderThan(now, CaptureService.StuckFiberMs)
      _     <- ZIO.when(stuck.nonEmpty)(
                 CaptureService.maybeCapture("STUCK_FIBER", s"stuckRequestFibers=${stuck.size}")
               )
      _     <- ZIO.sleep(1.second)
    yield ()).forever

  val runtimePressure: UIO[Unit] =
    (for
      rt        <- ZIO.succeed(java.lang.Runtime.getRuntime)
      used       = rt.totalMemory() - rt.freeMemory()
      ratio      = used.toDouble / rt.maxMemory().toDouble
      threads    = ManagementFactory.getThreadMXBean.getThreadCount
      overHeap   = ratio > CaptureService.HeapPressureRatio
      overThread = threads > CaptureService.ThreadPressureCount
      _         <- ZIO.when(overHeap || overThread)(
                     CaptureService.maybeCapture("RUNTIME_PRESSURE", s"heapRatio=${(ratio * 100).toInt}% threads=$threads")
                   )
      _         <- ZIO.sleep(1.second)
    yield ()).forever

  val all: UIO[Unit] =
    stuckFiber.forkDaemon *> runtimePressure.forkDaemon *> ZIO.unit
