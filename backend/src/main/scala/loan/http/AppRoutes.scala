package loan.http

import zio.*
import zio.http.*
import zio.json.*
import loan.domain.*
import java.util.UUID

object AppRoutes:

  private def parse[A](req: Request)(using JsonDecoder[A]): ZIO[Any, Response, A] =
    req.body.asString
      .orElseFail(Response.badRequest("cannot read body"))
      .flatMap(s => ZIO.fromEither(s.fromJson[A]).mapError(e => Response.badRequest(s"invalid body: $e")))

  private def serverError(t: Throwable): Response =
    Response.internalServerError(t.getMessage)

  val routes: Routes[LoanService, Response] = Routes(
    Method.GET / "api" / "health" -> handler(Response.text("ok")),

    Method.POST / "api" / "loan-requests" -> handler { (req: Request) =>
      for
        in <- parse[LoanRequestInput](req)
        _  <- ZIO.fail(Response.badRequest("invalid purpose")).unless(Purpose.valid(in.purpose))
        id <- ZIO.serviceWithZIO[LoanService](_.createLoanRequest(in)).mapError(serverError)
      yield Response.json(IdResponse(id.toString).toJson)
    },

    Method.POST / "api" / "users" -> handler { (req: Request) =>
      for
        in <- parse[UserInput](req)
        _  <- ZIO.serviceWithZIO[LoanService](_.createUser(in)).mapError(serverError)
      yield Response.json(UserResponse(in.email).toJson)
    },

    Method.GET / "api" / "loan-requests" / string("id") / "offers" -> handler { (id: String, _: Request) =>
      for
        uuid   <- ZIO.attempt(UUID.fromString(id)).mapError(_ => Response.badRequest("invalid id"))
        result <- ZIO.serviceWithZIO[LoanService](_.offers(uuid)).mapError(serverError)
        resp   <- result match
                    case None         => ZIO.succeed(Response.notFound("loan request not found"))
                    case Some(offers) => ZIO.succeed(Response.json(OffersResponse(offers).toJson))
      yield resp
    },

    Method.POST / "api" / "applications" -> handler { (req: Request) =>
      for
        in     <- parse[ApplicationInput](req)
        result <- ZIO.serviceWithZIO[LoanService](_.createApplication(in)).mapError(serverError)
        resp    = result match
                    case None     => Response.notFound("loan request not found")
                    case Some(id) => Response.json(ApplicationResponse(id.toString, "SUBMITTED").toJson)
      yield resp
    }
  ) @@ HeaderMiddleware.middleware
