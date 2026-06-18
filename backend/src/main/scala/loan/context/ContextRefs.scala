package loan.context

import zio.*

object ContextRefs:
  val correlationId: FiberRef[String] =
    Unsafe.unsafe(implicit u => FiberRef.unsafe.make(""))

  val userId: FiberRef[String] =
    Unsafe.unsafe(implicit u => FiberRef.unsafe.make(""))

  def get: UIO[RequestContext] =
    for
      c <- correlationId.get
      u <- userId.get
    yield RequestContext(c, u)

final case class RequestContext(correlationId: String, userId: String)
