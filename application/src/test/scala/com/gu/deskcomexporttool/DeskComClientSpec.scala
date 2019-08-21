package com.gu.deskcomexporttool

import cats.data.EitherT
import cats.instances.future._
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.{FlatSpec, Inside, MustMatchers}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.io.Source

class DeskComClientSpec extends FlatSpec with ScalaFutures with MustMatchers with IntegrationPatience {
  "DeskComClient" must "make getAllInteractions request and parse response" in {
    val sinceId = 32
    val pageSize = 123

    val mockHttpClient = new HttpClient {
      override def request(request: HttpRequest): EitherT[Future, HttpError, HttpResponse] = {
        request.method must equal("GET")
        request.headers must contain only HttpHeader("Authorization", "Basic dGVzdHVzZXI6dGVzdHBhc3N3b3Jk")
        request.url must equal(s"https://deskapi.com/api/v2/interactions?since_id=$sinceId&per_page=$pageSize&sort_field=created_at&sort_direction=asc")

        EitherT.rightT(HttpResponse(200, DeskComClientSpec.getAllInteractionsResponseBody))
      }

      override def close(): Unit = ()
    }
    val client = DeskComClient(DeskComApiConfig("https://deskapi.com", "testuser", "testpassword"), mockHttpClient)

    Inside.inside(client.getAllInteractions(sinceId, pageSize).value.futureValue) {
      case Right(interactionsResponse) =>
        interactionsResponse._embedded.entries must contain only InteractionFixture.interaction
        interactionsResponse._links.next.href must equal("/api/v2/interactions?per_page=2&since_id=1447484657")
    }
  }
  it must "return error if status is invalid" in {
    val mockHttpClient = new HttpClient {
      override def request(request: HttpRequest): EitherT[Future, HttpError, HttpResponse] = {
        EitherT.rightT(HttpResponse(400, "error response"))
      }

      override def close(): Unit = ()
    }
    val client = DeskComClient(DeskComApiConfig("https://deskapi.com", "testuser", "testpassword"), mockHttpClient)

    Inside.inside(client.getAllInteractions(0, 123).value.futureValue) {
      case Left(DeskComApiError(message)) =>
        message must equal("Interactions endpoint returned status: 400")
    }

  }
  it must "return error if http request fails" in {
    val mockHttpClient = new HttpClient {
      override def request(request: HttpRequest): EitherT[Future, HttpError, HttpResponse] = {
        EitherT.leftT(HttpError("request failed"))
      }

      override def close(): Unit = ()
    }
    val client = DeskComClient(DeskComApiConfig("https://deskapi.com", "testuser", "testpassword"), mockHttpClient)

    Inside.inside(client.getAllInteractions(32, 123).value.futureValue) {
      case Left(DeskComApiError(message)) =>
        message must equal("Request for interactions failed: HttpError(request failed)")
    }
  }
}

object DeskComClientSpec {
  lazy val getAllInteractionsResponseBody: String = Source.fromResource("getAllInteractionsResponseBody.json").mkString
}

