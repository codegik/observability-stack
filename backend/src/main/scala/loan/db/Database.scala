package loan.db

import zio.*
import java.sql.{Connection, DriverManager}
import scala.io.Source

final case class DbConfig(url: String, user: String, password: String)

object DbConfig:
  val fromEnv: UIO[DbConfig] = ZIO.succeed(
    DbConfig(
      sys.env.getOrElse("DB_URL", "jdbc:postgresql://localhost:5432/loan"),
      sys.env.getOrElse("DB_USER", "loan"),
      sys.env.getOrElse("DB_PASSWORD", "")
    )
  )

final class Database(pool: ZPool[Throwable, Connection]):
  def withConnection[A](f: Connection => A): Task[A] =
    ZIO.scoped(pool.get.flatMap(conn => ZIO.attemptBlocking(f(conn))))

  def runScript(resource: String): Task[Unit] =
    for
      sql <- ZIO.attempt {
               val is = getClass.getClassLoader.getResourceAsStream(resource)
               try Source.fromInputStream(is).mkString
               finally is.close()
             }
      _ <- withConnection { conn =>
             val st = conn.createStatement()
             try st.execute(sql)
             finally st.close()
           }
    yield ()

object Database:
  val layer: ZLayer[Any, Throwable, Database] =
    ZLayer.scoped {
      for
        cfg  <- DbConfig.fromEnv
        _    <- ZIO.attempt(Class.forName("org.postgresql.Driver"))
        pool <- ZPool.make(
                  ZIO.acquireRelease(
                    ZIO.attemptBlocking(DriverManager.getConnection(cfg.url, cfg.user, cfg.password))
                  )(conn => ZIO.attemptBlocking(conn.close()).orDie),
                  4 to 10,
                  60.seconds
                )
        db    = Database(pool)
        _    <- db.runScript("schema.sql")
        _    <- db.runScript("seed.sql")
        _    <- ZIO.logInfo("database schema applied and seeded")
      yield db
    }
