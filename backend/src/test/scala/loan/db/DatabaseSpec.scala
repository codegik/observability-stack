package loan.db

import zio.*
import zio.test.*

object DatabaseSpec extends ZIOSpecDefault:
  def spec = suite("Database")(
    test("schema applied and products seeded") {
      for
        db    <- ZIO.service[Database]
        count <- db.withConnection { conn =>
                   val rs = conn.createStatement().executeQuery("SELECT count(*) FROM loan_products")
                   rs.next()
                   rs.getInt(1)
                 }
      yield assertTrue(count >= 10)
    }
  ).provideShared(Database.layer)
