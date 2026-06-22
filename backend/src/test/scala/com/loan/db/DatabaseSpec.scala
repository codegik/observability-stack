package com.loan.db

import zio.*
import zio.test.*

object DatabaseSpec extends ZIOSpecDefault:
  def spec = suite("Database")(
    test("schema applied and products seeded") {
      for
        repos    <- ZIO.service[Repositories]
        products <- repos.allProducts
      yield assertTrue(products.size >= 10)
    }
  ).provideShared(Database.context >>> Repositories.layer)
