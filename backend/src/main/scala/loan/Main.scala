package loan

import zio.*

object Main extends ZIOAppDefault:
  def run = ZIO.logInfo("loan-backend starting")
