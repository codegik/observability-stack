package loan.domain

import zio.json.*
import java.util.UUID

object Purpose:
  val values: Set[String] =
    Set("HOME_IMPROVEMENT", "DEBT_CONSOLIDATION", "AUTO", "MEDICAL", "EDUCATION", "OTHER")
  def valid(p: String): Boolean = values.contains(p)

final case class Product(
  id: UUID,
  name: String,
  minAmount: BigDecimal,
  maxAmount: BigDecimal,
  minTermMonths: Int,
  maxTermMonths: Int,
  apr: BigDecimal,
  purpose: String
)

final case class LoanRequestRow(
  id: UUID,
  amount: BigDecimal,
  termMonths: Int,
  purpose: String,
  userUuid: String
)

final case class LoanRequestInput(amount: BigDecimal, termMonths: Int, purpose: String) derives JsonDecoder
final case class IdResponse(id: String) derives JsonEncoder

final case class UserInput(email: String, displayName: String, monthlyIncome: BigDecimal) derives JsonDecoder
final case class UserResponse(email: String) derives JsonEncoder

final case class ApplicationInput(loanRequestId: String, productId: String) derives JsonDecoder
final case class ApplicationResponse(id: String, status: String) derives JsonEncoder

final case class Offer(
  productId: String,
  name: String,
  apr: BigDecimal,
  offeredAmount: BigDecimal,
  offeredTermMonths: Int,
  monthlyPayment: BigDecimal,
  affordable: Boolean,
  amountDelta: BigDecimal,
  termDelta: Int
) derives JsonEncoder

final case class OffersResponse(offers: List[Offer]) derives JsonEncoder
