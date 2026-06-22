package com.loan.db

import zio.*
import io.getquill.*
import java.util.UUID
import com.loan.context.ContextRefs
import com.loan.domain.*

final case class LoanRequestEntity(
  id: UUID, amount: BigDecimal, termMonths: Int, purpose: String, userUuid: String, correlationId: String
)
final case class UserEntity(
  email: String, displayName: String, monthlyIncome: BigDecimal, correlationId: String, userUuid: String
)
final case class IdentityEntity(userUuid: String, email: Option[String], correlationId: String)
final case class ApplicationEntity(
  id: UUID, loanRequestId: UUID, productId: UUID, email: Option[String], status: String, userUuid: String, correlationId: String
)

final class Repositories(quill: Database.Ctx):
  import quill.*

  private inline def products     = quote { querySchema[Product]("loan_products") }
  private inline def loanRequests = quote { querySchema[LoanRequestEntity]("loan_requests") }
  private inline def users        = quote { querySchema[UserEntity]("users") }
  private inline def identities   = quote { querySchema[IdentityEntity]("user_identities") }
  private inline def applications = quote { querySchema[ApplicationEntity]("loan_applications") }

  def allProducts: Task[List[Product]] =
    quill.run(products)

  def insertLoanRequest(amount: BigDecimal, termMonths: Int, purpose: String): Task[UUID] =
    for
      ctx <- ContextRefs.get
      id  <- ZIO.succeed(UUID.randomUUID())
      e    = LoanRequestEntity(id, amount, termMonths, purpose, ctx.userId, ctx.correlationId)
      _   <- quill.run(quote(loanRequests.insertValue(lift(e))))
    yield id

  def findLoanRequest(id: UUID): Task[Option[LoanRequestRow]] =
    quill
      .run(quote(loanRequests.filter(_.id == lift(id))))
      .map(_.headOption.map(e => LoanRequestRow(e.id, e.amount, e.termMonths, e.purpose, e.userUuid)))

  def upsertUser(email: String, displayName: String, monthlyIncome: BigDecimal): Task[Unit] =
    for
      ctx <- ContextRefs.get
      u    = UserEntity(email, displayName, monthlyIncome, ctx.correlationId, ctx.userId)
      _   <- quill.run(quote(
               users
                 .insertValue(lift(u))
                 .onConflictUpdate(_.email)(
                   (t, e) => t.displayName -> e.displayName,
                   (t, e) => t.monthlyIncome -> e.monthlyIncome
                 )
             ))
      idn  = IdentityEntity(ctx.userId, Some(email), ctx.correlationId)
      _   <- quill.run(quote(
               identities
                 .insertValue(lift(idn))
                 .onConflictUpdate(_.userUuid)((t, e) => t.email -> e.email)
             ))
    yield ()

  def emailByUserUuid(userUuid: String): Task[Option[String]] =
    quill
      .run(quote(identities.filter(_.userUuid == lift(userUuid)).map(_.email)))
      .map(_.headOption.flatten)

  def incomeByUserUuid(userUuid: String): Task[Option[BigDecimal]] =
    emailByUserUuid(userUuid).flatMap {
      case Some(e) => quill.run(quote(users.filter(_.email == lift(e)).map(_.monthlyIncome))).map(_.headOption)
      case None    => ZIO.none
    }

  def insertApplication(loanRequestId: UUID, productId: UUID, email: Option[String], status: String): Task[UUID] =
    for
      ctx <- ContextRefs.get
      id  <- ZIO.succeed(UUID.randomUUID())
      a    = ApplicationEntity(id, loanRequestId, productId, email, status, ctx.userId, ctx.correlationId)
      _   <- quill.run(quote(applications.insertValue(lift(a))))
    yield id

object Repositories:
  val layer: ZLayer[Database.Ctx, Nothing, Repositories] =
    ZLayer.fromFunction(Repositories(_))
