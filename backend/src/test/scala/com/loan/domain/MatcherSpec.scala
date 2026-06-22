package com.loan.domain

import zio.test.*
import java.util.UUID

object MatcherSpec extends ZIOSpecDefault:

  private def product(name: String, lo: Int, hi: Int, tlo: Int, thi: Int, apr: String, purpose: String) =
    Product(UUID.randomUUID(), name, BigDecimal(lo), BigDecimal(hi), tlo, thi, BigDecimal(apr), purpose)

  private val products = List(
    product("Auto A", 3000, 25000, 12, 60, "6.90", "AUTO"),
    product("Auto B", 25000, 80000, 24, 72, "5.49", "AUTO"),
    product("Auto C", 1000, 40000, 6, 72, "12.90", "AUTO"),
    product("Auto D", 1000, 40000, 6, 72, "9.90", "AUTO"),
    product("Home A", 2000, 15000, 12, 48, "9.90", "HOME_IMPROVEMENT")
  )

  def spec = suite("Matcher")(
    test("returns at most TopN offers") {
      val offers = Matcher.best(products, BigDecimal(18000), 36, "AUTO", BigDecimal(5000))
      assertTrue(offers.size == Matcher.TopN)
    },
    test("only matches the requested purpose when products exist for it") {
      val offers = Matcher.best(products, BigDecimal(10000), 36, "AUTO", BigDecimal(5000))
      assertTrue(offers.forall(o => o.name.startsWith("Auto")))
    },
    test("never returns empty even when no product matches the purpose") {
      val offers = Matcher.best(products, BigDecimal(10000), 36, "MEDICAL", BigDecimal(5000))
      assertTrue(offers.nonEmpty)
    },
    test("flags an offer unaffordable when payment exceeds the income share") {
      val offers = Matcher.best(products, BigDecimal(40000), 12, "AUTO", BigDecimal(800))
      assertTrue(offers.exists(!_.affordable))
    },
    test("affordable offers are ranked ahead of unaffordable ones") {
      val offers = Matcher.best(products, BigDecimal(20000), 24, "AUTO", BigDecimal(1500))
      val firstUnaffordable = offers.indexWhere(!_.affordable)
      val lastAffordable    = offers.lastIndexWhere(_.affordable)
      assertTrue(firstUnaffordable == -1 || lastAffordable == -1 || lastAffordable < firstUnaffordable)
    },
    test("monthly payment matches amortization for a zero-interest edge") {
      val payment = Matcher.monthlyPayment(BigDecimal(1200), BigDecimal(0), 12)
      assertTrue(payment == BigDecimal("100.00"))
    }
  )
