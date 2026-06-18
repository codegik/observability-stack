package loan.domain

import zio.*
import java.util.UUID
import loan.db.Repositories

final class LoanService(repos: Repositories):

  def createLoanRequest(in: LoanRequestInput): Task[UUID] =
    repos.insertLoanRequest(in.amount, in.termMonths, in.purpose) <*
      ZIO.logInfo(s"loan request created amount=${in.amount} term=${in.termMonths} purpose=${in.purpose}")

  def createUser(in: UserInput): Task[Unit] =
    repos.upsertUser(in.email, in.displayName, in.monthlyIncome) <*
      ZIO.logInfo(s"user upserted email=${in.email}")

  def offers(loanRequestId: UUID): Task[Option[List[Offer]]] =
    repos.findLoanRequest(loanRequestId).flatMap {
      case None => ZIO.none
      case Some(req) =>
        for
          incomeOpt <- repos.incomeByUserUuid(req.userUuid)
          products  <- repos.allProducts
          income     = incomeOpt.getOrElse(BigDecimal(0))
          offers     = Matcher.best(products, req.amount, req.termMonths, req.purpose, income)
          _         <- ZIO.logInfo(s"offers computed request=$loanRequestId count=${offers.size}")
        yield Some(offers)
    }

  def createApplication(in: ApplicationInput): Task[Option[UUID]] =
    for
      reqId  <- ZIO.attempt(UUID.fromString(in.loanRequestId))
      prodId <- ZIO.attempt(UUID.fromString(in.productId))
      reqOpt <- repos.findLoanRequest(reqId)
      res    <- reqOpt match
                  case None => ZIO.none
                  case Some(req) =>
                    for
                      email <- repos.emailByUserUuid(req.userUuid)
                      id    <- repos.insertApplication(req.id, prodId, email, "SUBMITTED")
                      _     <- ZIO.logInfo(s"application created id=$id request=${req.id}")
                    yield Some(id)
    yield res

object LoanService:
  val layer: ZLayer[Repositories, Nothing, LoanService] =
    ZLayer.fromFunction(LoanService(_))
