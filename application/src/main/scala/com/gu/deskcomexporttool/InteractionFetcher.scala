package com.gu.deskcomexporttool

import cats.data.EitherT
import cats.instances.future._

import scala.concurrent.{ExecutionContext, Future}

trait InteractionFetcher {
  def getInteractions(sinceId: String): EitherT[Future, InteractionFetcherError, Interactions]
  def close(): Unit
}

object InteractionFetcher {
  def apply(deskComClient: DeskComClient, pageSize: Int)(implicit executionContext: ExecutionContext): InteractionFetcher = new InteractionFetcher() {
    override def getInteractions(sinceId: String): EitherT[Future, InteractionFetcherError, Interactions] =
      deskComClient
        .getAllInteractions(sinceId, pageSize)
        .bimap(
          { error =>
            InteractionFetcherError(s"get interactions request failed: $error")
          },
          { interactionsResponse =>
            Interactions(interactionsResponse._embedded.entries, "0")
          }
        )


    override def close(): Unit = deskComClient.close()
  }
}

case class Interactions(interactions: List[Interaction], nextBatchSinceId: String)

case class InteractionFetcherError(message: String)

trait InteractionFetcherFactory {
  def create(config: DeskComApiConfig, pageSize: Int): InteractionFetcher
}

object InteractionFetcherFactory {
  def apply()(implicit ec: ExecutionContext): InteractionFetcherFactory = new InteractionFetcherFactory() {
    override def create(config: DeskComApiConfig, pageSize: Int): InteractionFetcher = {
      InteractionFetcher(DeskComClient(config, HttpClient()), pageSize)
    }
  }
}