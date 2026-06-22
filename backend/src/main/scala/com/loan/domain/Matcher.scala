package com.loan.domain

import scala.math.BigDecimal.RoundingMode

object Matcher:
  val AffordabilityShare: BigDecimal = BigDecimal("0.40")
  val TopN = 3

  def monthlyPayment(amount: BigDecimal, apr: BigDecimal, termMonths: Int): BigDecimal =
    val r = apr / BigDecimal(1200)
    val raw =
      if r == 0 then amount / termMonths
      else
        val factor = math.pow((1 + r.toDouble), -termMonths.toDouble)
        amount * r / (1 - BigDecimal(factor))
    raw.setScale(2, RoundingMode.HALF_UP)

  private def clamp(v: BigDecimal, lo: BigDecimal, hi: BigDecimal): BigDecimal =
    if v < lo then lo else if v > hi then hi else v

  private def clampI(v: Int, lo: Int, hi: Int): Int =
    if v < lo then lo else if v > hi then hi else v

  def offerFor(p: Product, amount: BigDecimal, termMonths: Int, monthlyIncome: BigDecimal): Offer =
    val offeredAmount = clamp(amount, p.minAmount, p.maxAmount)
    val offeredTerm   = clampI(termMonths, p.minTermMonths, p.maxTermMonths)
    val payment       = monthlyPayment(offeredAmount, p.apr, offeredTerm)
    val affordable    = payment <= AffordabilityShare * monthlyIncome
    Offer(
      productId = p.id.toString,
      name = p.name,
      apr = p.apr,
      offeredAmount = offeredAmount,
      offeredTermMonths = offeredTerm,
      monthlyPayment = payment,
      affordable = affordable,
      amountDelta = (offeredAmount - amount).abs,
      termDelta = math.abs(offeredTerm - termMonths)
    )

  private def score(o: Offer, amount: BigDecimal, termMonths: Int, monthlyIncome: BigDecimal): Double =
    val amountNorm = if amount == 0 then 0.0 else (o.amountDelta / amount).toDouble
    val termNorm   = if termMonths == 0 then 0.0 else o.termDelta.toDouble / termMonths.toDouble
    val affordPenalty =
      if o.affordable then 0.0
      else
        val cap = (AffordabilityShare * monthlyIncome)
        if cap <= 0 then 100.0 else ((o.monthlyPayment - cap) / cap).toDouble
    val aprFactor = o.apr.toDouble / 100.0
    amountNorm + termNorm + 2.0 * affordPenalty + aprFactor

  def best(
    products: List[Product],
    amount: BigDecimal,
    termMonths: Int,
    purpose: String,
    monthlyIncome: BigDecimal
  ): List[Offer] =
    val byPurpose = products.filter(_.purpose == purpose)
    val candidates = if byPurpose.nonEmpty then byPurpose else products
    candidates
      .map(p => offerFor(p, amount, termMonths, monthlyIncome))
      .sortBy(o => (!o.affordable, score(o, amount, termMonths, monthlyIncome)))
      .take(TopN)
