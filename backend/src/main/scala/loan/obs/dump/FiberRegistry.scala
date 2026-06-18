package loan.obs.dump

import java.util.concurrent.ConcurrentHashMap
import scala.jdk.CollectionConverters.*
import loan.context.RequestContext

final case class JourneyEntry(fiberKey: String, correlationId: String, userId: String, startMs: Long)

object FiberRegistry:
  private val entries = new ConcurrentHashMap[String, JourneyEntry]()

  def register(fiberKey: String, ctx: RequestContext, startMs: Long): Unit =
    entries.put(fiberKey, JourneyEntry(fiberKey, ctx.correlationId, ctx.userId, startMs))

  def unregister(fiberKey: String): Unit =
    entries.remove(fiberKey)

  def snapshot: List[JourneyEntry] =
    entries.values().asScala.toList

  def olderThan(nowMs: Long, ageMs: Long): List[JourneyEntry] =
    snapshot.filter(e => nowMs - e.startMs > ageMs)
