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
    val page = 32
    val pageSize = 123

    val mockHttpClient = new HttpClient {
      override def request(request: HttpRequest): EitherT[Future, HttpError, HttpResponse] = {
        request.method must equal("GET")
        request.headers must contain only HttpHeader("Authorization", "Basic dGVzdHVzZXI6dGVzdHBhc3N3b3Jk")
        request.url must equal(s"https://deskapi.com/api/v2/interactions?page=$page&per_page=$pageSize&sort_field=created_at&sort_direction=asc")

        EitherT.right(Future.successful(HttpResponse(200, DeskComClientSpec.getAllInteractionsResponseBody)))
      }
    }
    val client = DeskComClient(DeskComApiConfig("https://deskapi.com", "testuser", "testpassword"), mockHttpClient)

    Inside.inside(client.getAllInteractions(page, pageSize).value.futureValue) {
      case Right(interactions) =>
        interactions must contain only (
          Interaction(1111, "2018-01-01T01:01:01Z", "2019-01-01T01:01:01Z", "test body 1111",
            "Test User 1111 <testuser1111@test.com>", "<toaddress1111@test.com>", "<ccaddress1111@test.com>",
            "<bccaddress1111@test.com>", "in", "received", "Test Subject 1111"
          )
        )
    }
  }
}

object DeskComClientSpec {
  lazy val getAllInteractionsResponseBody = Source.fromResource("getAllInteractionsResponseBody.json").mkString

}

