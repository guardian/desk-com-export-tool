package com.gu.deskcomexporttool

import cats.data.EitherT
import cats.instances.future._
import org.slf4j.LoggerFactory

import scala.concurrent.{ExecutionContext, Future}

trait InteractionFetcher {
  def getInteractions(sinceId: String): EitherT[Future, InteractionFetcherError, Interactions]

  def close(): Unit
}

object InteractionFetcher {
  private val log = LoggerFactory.getLogger(this.getClass)

  def apply(deskComClient: DeskComClient, pageSize: Int)(implicit executionContext: ExecutionContext): InteractionFetcher = new InteractionFetcher() {
    override def getInteractions(sinceId: String): EitherT[Future, InteractionFetcherError, Interactions] =
      for {
        apiResponse <- callGetInteractionsApi(sinceId)
        nextId <- EitherT.fromEither(parseNextLink(apiResponse._links.next))
      } yield Interactions(apiResponse._embedded.entries, nextId)

    private def callGetInteractionsApi(sinceId: String) = {
      deskComClient
        .getAllInteractions(sinceId, pageSize)
        .leftMap[InteractionFetcherError] {
          case DeskComUnprocessableEntity(message) =>
            log.info(s"received 422 repsonse from desk.com, assuming no more interactions: $message")
            InteractionFetcherNoMoreInteractions()
          case DeskComUnexpectedApiError(message) =>
            InteractionFetcherUnexpectedError(s"get interactions request failed: $message")
        }
    }

    override def close(): Unit = deskComClient.close()

    private val NextLinkRegex = """.*?since_id=(.*?)(?:&.*|$)""".r

    private def parseNextLink(nextLink: Link): Either[InteractionFetcherError, String] = {
      nextLink.href match {
        case NextLinkRegex(sinceId) => Right(sinceId)
        case invalidLink => Left(InteractionFetcherUnexpectedError(s"Failed to parse next link: $invalidLink"))
      }
    }
  }
}

case class Interactions(interactions: List[Interaction], nextBatchSinceId: String)

sealed trait InteractionFetcherError

case class InteractionFetcherUnexpectedError(message: String) extends InteractionFetcherError

case class InteractionFetcherNoMoreInteractions() extends InteractionFetcherError

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