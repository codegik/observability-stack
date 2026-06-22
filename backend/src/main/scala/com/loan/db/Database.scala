package com.loan.db

import zio.*
import io.getquill.SnakeCase
import io.getquill.jdbczio.Quill
import com.zaxxer.hikari.{HikariConfig, HikariDataSource}
import javax.sql.DataSource
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

object Database:
  type Ctx = Quill.Postgres[SnakeCase]

  val dataSource: ZLayer[Any, Throwable, DataSource] =
    ZLayer.scoped {
      for
        cfg <- DbConfig.fromEnv
        ds  <- ZIO.acquireRelease(ZIO.attempt {
                 val hc = new HikariConfig()
                 hc.setJdbcUrl(cfg.url)
                 hc.setUsername(cfg.user)
                 hc.setPassword(cfg.password)
                 hc.setMaximumPoolSize(10)
                 new HikariDataSource(hc)
               })(ds => ZIO.attempt(ds.close()).orDie)
      yield ds
    }

  val context: ZLayer[Any, Throwable, Ctx] =
    dataSource >>> ZLayer.scoped {
      for
        ds <- ZIO.service[DataSource]
        _  <- bootstrap
        _  <- ZIO.logInfo("database schema applied and seeded")
      yield new Quill.Postgres(SnakeCase, ds)
    }

  private val bootstrap: ZIO[DataSource, Throwable, Unit] =
    runScript("schema.sql") *> runScript("seed.sql")

  private def runScript(resource: String): ZIO[DataSource, Throwable, Unit] =
    for
      ds  <- ZIO.service[DataSource]
      sql <- ZIO.attempt {
               val is = getClass.getClassLoader.getResourceAsStream(resource)
               try Source.fromInputStream(is).mkString finally is.close()
             }
      _   <- ZIO.attemptBlocking {
               val conn = ds.getConnection
               try
                 val st = conn.createStatement()
                 try st.execute(sql) finally st.close()
               finally conn.close()
             }
    yield ()
