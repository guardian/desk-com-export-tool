package com.gu.deskcomexporttool

import cats.data.EitherT
import cats.instances.future._
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.{FlatSpec, Inside, MustMatchers}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class InteractionFetcherSpec extends FlatSpec with ScalaFutures with MustMatchers with IntegrationPatience {
  "InteractionFetcher" must "parse next link correctly" in {
    val testPageSize = 12
    val startSinceId = "start-since-id"
    val nextSinceId = "next-since-id"

    val mockDeskComClient = new DeskComClient {
      override def getAllInteractions(sinceId: String, pageSize: Int): EitherT[Future, DeskComApiError, GetInteractionsResponse] = {
        pageSize must equal(testPageSize)
        sinceId must equal(sinceId)
        EitherT.rightT(
          GetInteractionsResponse(
            InteractionResultsLinks(next = Link(s"/api/v2/interactions?per_page=$testPageSize&since_id=$nextSinceId")),
            GetInteractionsEmbedded(List(InteractionFixture.interaction))
          )
        )
      }

      override def close(): Unit = ???
    }

    Inside.inside(InteractionFetcher(mockDeskComClient, testPageSize).getInteractions(startSinceId).value.futureValue) {
      case Right(interactions) =>
        interactions.interactions must contain only(InteractionFixture.interaction)
        interactions.nextBatchSinceId must equal(nextSinceId)
    }
  }
}
