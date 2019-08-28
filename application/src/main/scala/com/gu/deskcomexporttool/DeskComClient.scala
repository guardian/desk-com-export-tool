package com.gu.deskcomexporttool

import java.util.Base64

import cats.data.EitherT
import cats.instances.future._
import cats.syntax.either._
import com.gu.deskcomexporttool.Logging.truncate
import io.circe.derivation._
import io.circe.parser._
import io.circe.{Decoder, derivation}
import org.slf4j.LoggerFactory

import scala.concurrent.{ExecutionContext, Future}

trait DeskComClient {
  def getAllInteractions(sinceId: String, pageSize: Int): EitherT[Future, DeskComApiError, GetInteractionsResponse]

  def close(): Unit
}

object DeskComClient {
  private val log = LoggerFactory.getLogger(this.getClass)

  def apply(config: DeskComApiConfig, httpClient: HttpClient)(implicit ec: ExecutionContext): DeskComClient = new DeskComClient() {
    private val authHeader = HttpHeader(
      "Authorization",
      s"Basic ${Base64.getEncoder.encodeToString(s"${config.username}:${config.password}".getBytes("UTF-8"))}"
    )

    implicit val interactionLinkDecoder: Decoder[Link] = deriveDecoder(derivation.renaming.snakeCase)
    implicit val interactionLinksDecoder: Decoder[InteractionLinks] = deriveDecoder(derivation.renaming.snakeCase)
    implicit val interactionResultsLinksDecoder: Decoder[InteractionResultsLinks] = deriveDecoder(derivation.renaming.snakeCase)
    implicit val interactionDecoder: Decoder[Interaction] = deriveDecoder(derivation.renaming.snakeCase)
    implicit val interactionEmbeddedDecoder: Decoder[GetInteractionsEmbedded] = deriveDecoder(derivation.renaming.snakeCase)
    implicit val interactionResponseDecoder: Decoder[GetInteractionsResponse] = deriveDecoder(derivation.renaming.snakeCase)

    override def getAllInteractions(sinceId: String, pageSize: Int): EitherT[Future, DeskComApiError, GetInteractionsResponse] = {
      for {
        httpResponse <- httpClient.request(
          HttpRequest(
            "GET",
            s"${config.baseUrl}/api/v2/interactions?since_id=$sinceId&per_page=$pageSize",
            List(authHeader)
          )
        ).leftMap(httpError => DeskComUnexpectedApiError(s"Request for interactions failed: $httpError"))
        _ <- EitherT.fromEither(validateStatusCode(httpResponse))
        parsedResponseBody <- EitherT.fromEither(parseGetAllInteractions(httpResponse.body))
      } yield parsedResponseBody
    }

    private def parseGetAllInteractions(body: String): Either[DeskComApiError, GetInteractionsResponse] = {
      decode[GetInteractionsResponse](body)
        .leftMap { parsingFailure =>
          log.debug(s"Failed to parse response response: $body error:$parsingFailure")
          DeskComUnexpectedApiError(s"Failed to parse interaction response: $parsingFailure")
        }
    }

    private def validateStatusCode(httpResponse: HttpResponse): Either[DeskComApiError, Unit] = {
      httpResponse.statusCode match {
        case 200 => Right(())
        case 422 => Left(DeskComUnprocessableEntity(truncate(httpResponse.body)))
        case statusCode => Left(DeskComUnexpectedApiError(s"Interactions endpoint returned status: $statusCode"))
      }
    }

    override def close(): Unit = httpClient.close()
  }
}

case class Interaction(id: Option[Long], createdAt: String, updatedAt: String, body: Option[String], from: Option[String], to: Option[String],
                       cc: Option[String], bcc: Option[String], direction: Option[String], status: Option[String], subject: Option[String],
                       _links: InteractionLinks)

case class InteractionLinks(self: Link)

case class InteractionResultsLinks(next: Link)

case class Link(href: String)

case class GetInteractionsEmbedded(entries: List[Interaction])

case class GetInteractionsResponse(_links: InteractionResultsLinks, _embedded: GetInteractionsEmbedded)

case class DeskComApiConfig(baseUrl: String, username: String, password: String)

sealed trait DeskComApiError

//Indicates there are no more results
case class DeskComUnexpectedApiError(message: String) extends DeskComApiError

case class DeskComUnprocessableEntity(message: String) extends DeskComApiError
