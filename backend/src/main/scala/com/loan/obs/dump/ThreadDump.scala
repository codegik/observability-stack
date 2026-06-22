package com.loan.obs.dump

import zio.*
import java.lang.management.ManagementFactory

object ThreadDump:
  def text: UIO[String] = ZIO.succeed {
    val mx = ManagementFactory.getThreadMXBean
    mx.dumpAllThreads(true, true).map(_.toString).mkString("\n")
  }
