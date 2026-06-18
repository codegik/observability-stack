package loan.db

import zio.*
import java.util.UUID
import loan.context.ContextRefs
import loan.domain.*

final class Repositories(db: Database):

  def allProducts: Task[List[Product]] =
    db.withConnection { conn =>
      val rs = conn.createStatement().executeQuery(
        "SELECT id,name,min_amount,max_amount,min_term_months,max_term_months,apr,purpose FROM loan_products"
      )
      val buf = scala.collection.mutable.ListBuffer.empty[Product]
      while rs.next() do
        buf += Product(
          UUID.fromString(rs.getString("id")),
          rs.getString("name"),
          BigDecimal(rs.getBigDecimal("min_amount")),
          BigDecimal(rs.getBigDecimal("max_amount")),
          rs.getInt("min_term_months"),
          rs.getInt("max_term_months"),
          BigDecimal(rs.getBigDecimal("apr")),
          rs.getString("purpose")
        )
      buf.toList
    }

  def insertLoanRequest(amount: BigDecimal, termMonths: Int, purpose: String): Task[UUID] =
    for
      ctx <- ContextRefs.get
      id  <- ZIO.succeed(UUID.randomUUID())
      _   <- db.withConnection { conn =>
               val ps = conn.prepareStatement(
                 "INSERT INTO loan_requests (id,amount,term_months,purpose,user_uuid,correlation_id) VALUES (?,?,?,?,?,?)"
               )
               ps.setObject(1, id)
               ps.setBigDecimal(2, amount.bigDecimal)
               ps.setInt(3, termMonths)
               ps.setString(4, purpose)
               ps.setString(5, ctx.userId)
               ps.setString(6, ctx.correlationId)
               try ps.executeUpdate() finally ps.close()
             }
    yield id

  def findLoanRequest(id: UUID): Task[Option[LoanRequestRow]] =
    db.withConnection { conn =>
      val ps = conn.prepareStatement(
        "SELECT id,amount,term_months,purpose,user_uuid FROM loan_requests WHERE id=?"
      )
      ps.setObject(1, id)
      val rs = ps.executeQuery()
      if rs.next() then
        Some(LoanRequestRow(
          UUID.fromString(rs.getString("id")),
          BigDecimal(rs.getBigDecimal("amount")),
          rs.getInt("term_months"),
          rs.getString("purpose"),
          rs.getString("user_uuid")
        ))
      else None
    }

  def upsertUser(email: String, displayName: String, monthlyIncome: BigDecimal): Task[Unit] =
    for
      ctx <- ContextRefs.get
      _   <- db.withConnection { conn =>
               val ps = conn.prepareStatement(
                 """INSERT INTO users (email,display_name,monthly_income,correlation_id,user_uuid)
                    VALUES (?,?,?,?,?)
                    ON CONFLICT (email) DO UPDATE SET display_name=EXCLUDED.display_name, monthly_income=EXCLUDED.monthly_income"""
               )
               ps.setString(1, email)
               ps.setString(2, displayName)
               ps.setBigDecimal(3, monthlyIncome.bigDecimal)
               ps.setString(4, ctx.correlationId)
               ps.setString(5, ctx.userId)
               try ps.executeUpdate() finally ps.close()
               val ps2 = conn.prepareStatement(
                 """INSERT INTO user_identities (user_uuid,email,correlation_id)
                    VALUES (?,?,?)
                    ON CONFLICT (user_uuid) DO UPDATE SET email=EXCLUDED.email"""
               )
               ps2.setString(1, ctx.userId)
               ps2.setString(2, email)
               ps2.setString(3, ctx.correlationId)
               try ps2.executeUpdate() finally ps2.close()
             }
    yield ()

  def incomeByUserUuid(userUuid: String): Task[Option[BigDecimal]] =
    db.withConnection { conn =>
      val ps = conn.prepareStatement(
        "SELECT u.monthly_income FROM user_identities i JOIN users u ON u.email=i.email WHERE i.user_uuid=?"
      )
      ps.setString(1, userUuid)
      val rs = ps.executeQuery()
      if rs.next() then Option(rs.getBigDecimal(1)).map(BigDecimal(_)) else None
    }

  def emailByUserUuid(userUuid: String): Task[Option[String]] =
    db.withConnection { conn =>
      val ps = conn.prepareStatement("SELECT email FROM user_identities WHERE user_uuid=?")
      ps.setString(1, userUuid)
      val rs = ps.executeQuery()
      if rs.next() then Option(rs.getString(1)) else None
    }

  def insertApplication(loanRequestId: UUID, productId: UUID, email: Option[String], status: String): Task[UUID] =
    for
      ctx <- ContextRefs.get
      id  <- ZIO.succeed(UUID.randomUUID())
      _   <- db.withConnection { conn =>
               val ps = conn.prepareStatement(
                 "INSERT INTO loan_applications (id,loan_request_id,product_id,email,status,user_uuid,correlation_id) VALUES (?,?,?,?,?,?,?)"
               )
               ps.setObject(1, id)
               ps.setObject(2, loanRequestId)
               ps.setObject(3, productId)
               email match
                 case Some(e) => ps.setString(4, e)
                 case None    => ps.setNull(4, java.sql.Types.VARCHAR)
               ps.setString(5, status)
               ps.setString(6, ctx.userId)
               ps.setString(7, ctx.correlationId)
               try ps.executeUpdate() finally ps.close()
             }
    yield id

object Repositories:
  val layer: ZLayer[Database, Nothing, Repositories] =
    ZLayer.fromFunction(Repositories(_))
