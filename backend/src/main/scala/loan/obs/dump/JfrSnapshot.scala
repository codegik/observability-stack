package loan.obs.dump

import java.nio.file.{Files, Path}

object JfrSnapshot:
  def write(targetDir: Path): Unit =
    try
      val recorder = jdk.jfr.FlightRecorder.getFlightRecorder
      val snapshot = recorder.takeSnapshot()
      try snapshot.dump(targetDir.resolve("snapshot.jfr"))
      finally snapshot.close()
    catch
      case t: Throwable =>
        Files.writeString(targetDir.resolve("snapshot.unavailable.txt"), s"JFR snapshot unavailable: ${t.getMessage}")
        ()
