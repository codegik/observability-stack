package com.loan.obs.dump

import zio.*
import zio.json.*
import java.nio.file.{Files, Path, Paths}
import java.util.UUID
import java.util.concurrent.{ConcurrentHashMap, TimeUnit}
import java.util.concurrent.atomic.AtomicInteger
import com.loan.context.ContextRefs

final case class CaptureMeta(
  id: String,
  trigger: String,
  correlationId: String,
  userId: String,
  traceId: String,
  measurement: String,
  timestampMs: Long
) derives JsonCodec

object CaptureService:
  private val dumpDir: Path = Paths.get(sys.env.getOrElse("DUMP_DIR", "/tmp/loan-dumps"))

  val LatencyThresholdMs: Long  = sys.env.get("LATENCY_THRESHOLD_MS").map(_.toLong).getOrElse(2000L)
  val StuckFiberMs: Long        = sys.env.get("STUCK_FIBER_MS").map(_.toLong).getOrElse(10000L)
  val HeapPressureRatio: Double = sys.env.get("HEAP_PRESSURE_RATIO").map(_.toDouble).getOrElse(0.85)
  val ThreadPressureCount: Int  = sys.env.get("THREAD_PRESSURE_COUNT").map(_.toInt).getOrElse(400)
  private val cooldownMs: Long  = sys.env.get("CAPTURE_COOLDOWN_MS").map(_.toLong).getOrElse(10000L)
  private val retainN: Int      = sys.env.get("CAPTURE_RETAIN_N").map(_.toInt).getOrElse(20)

  private val lastByKind = new ConcurrentHashMap[String, java.lang.Long]()
  private val suppressed = new ConcurrentHashMap[String, AtomicInteger]()

  val init: UIO[Unit] = ZIO.attempt(Files.createDirectories(dumpDir)).ignore

  def suppressedCount(trigger: String): Int =
    Option(suppressed.get(trigger)).map(_.get()).getOrElse(0)

  def maybeCapture(trigger: String, measurement: String): UIO[Option[String]] =
    Clock.currentTime(TimeUnit.MILLISECONDS).flatMap { now =>
      val last = Option(lastByKind.get(trigger)).map(_.longValue).getOrElse(0L)
      if now - last >= cooldownMs then
        lastByKind.put(trigger, now)
        capture(trigger, measurement, now).map(Some(_))
      else
        ZIO.succeed(suppressed.computeIfAbsent(trigger, _ => new AtomicInteger()).incrementAndGet()) *>
          ZIO.logInfo(s"capture suppressed trigger=$trigger (cooldown)").as(None)
    }

  private def capture(trigger: String, measurement: String, nowMs: Long): UIO[String] =
    for
      id      <- ZIO.succeed(UUID.randomUUID().toString)
      ctx     <- ContextRefs.get
      threads <- ThreadDump.text
      fibers  <- FiberDump.text
      traceId <- ContextRefs.traceId.get
      journeys = FiberRegistry.snapshot
                   .map(e => s"${e.fiberKey} -> correlation_id=${e.correlationId} user_id=${e.userId} ageMs=${nowMs - e.startMs}")
                   .mkString("\n")
      meta     = CaptureMeta(id, trigger, ctx.correlationId, ctx.userId, traceId, measurement, nowMs)
      _       <- ZIO.attemptBlocking {
                   val dir = dumpDir.resolve(id)
                   Files.createDirectories(dir)
                   Files.writeString(dir.resolve("threads.txt"), threads)
                   Files.writeString(dir.resolve("fibers.txt"), fibers)
                   Files.writeString(dir.resolve("fiber-journeys.txt"), journeys)
                   Files.writeString(dir.resolve("meta.json"), meta.toJson)
                   JfrSnapshot.write(dir)
                 }.ignore
      _       <- enforceRetention
      _       <- ZIO.logAnnotate("capture_id", id) {
                   ZIO.logAnnotate("trigger", trigger) {
                     ZIO.logAnnotate("correlation_id", ctx.correlationId) {
                       ZIO.logAnnotate("user_id", ctx.userId) {
                         ZIO.logInfo(s"capture stored $measurement")
                       }
                     }
                   }
                 }
    yield id

  private def enforceRetention: UIO[Unit] =
    ZIO.attemptBlocking {
      val dirs   = Option(dumpDir.toFile.listFiles).getOrElse(Array.empty[java.io.File]).filter(_.isDirectory)
      val sorted = dirs.sortBy(_.lastModified)
      val excess = sorted.length - retainN
      if excess > 0 then sorted.take(excess).foreach(deleteRecursively)
    }.ignore

  private def deleteRecursively(f: java.io.File): Unit =
    if f.isDirectory then Option(f.listFiles).getOrElse(Array.empty[java.io.File]).foreach(deleteRecursively)
    f.delete()
    ()

  def list: UIO[List[CaptureMeta]] =
    ZIO.attemptBlocking {
      val dirs = Option(dumpDir.toFile.listFiles).getOrElse(Array.empty[java.io.File]).filter(_.isDirectory)
      dirs.flatMap { d =>
        val mf = d.toPath.resolve("meta.json")
        if Files.exists(mf) then Files.readString(mf).fromJson[CaptureMeta].toOption else None
      }.toList.sortBy(-_.timestampMs)
    }.orElseSucceed(Nil)

  def get(id: String): UIO[Option[String]] =
    ZIO.attemptBlocking {
      val dir = dumpDir.resolve(id)
      val mf  = dir.resolve("meta.json")
      if Files.exists(mf) then
        val meta  = Files.readString(mf)
        val files = Option(dir.toFile.listFiles).getOrElse(Array.empty[java.io.File]).map(_.getName).sorted.mkString("\",\"")
        Some(s"""{"meta":$meta,"files":["$files"]}""")
      else None
    }.orElseSucceed(None)
