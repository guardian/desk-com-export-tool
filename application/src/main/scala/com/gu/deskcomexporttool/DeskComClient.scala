package com.gu.deskcomexporttool

import java.util.Base64

import cats.data.EitherT
import cats.instances.future._
import io.circe.derivation._
import io.circe.parser._
import io.circe.{Decoder, derivation}

import scala.concurrent.{ExecutionContext, Future}

trait DeskComClient {
  def getAllInteractions(page: Int, pageSize: Int): EitherT[Future, DeskComApiError, List[Interaction]]
}

object DeskComClient {
  def apply(config: DeskComApiConfig, httpClient: HttpClient)(implicit ec: ExecutionContext): DeskComClient = new DeskComClient() {
    private val authHeader = HttpHeader(
      "Authorization",
      s"Basic ${Base64.getEncoder.encodeToString(s"${config.username}:${config.password}".getBytes("UTF-8"))}"
    )

    implicit val interactionDecoder: Decoder[Interaction] = deriveDecoder(derivation.renaming.snakeCase)
    implicit val interactionEmbeddedDecoder: Decoder[GetInteractionsEmbedded] = deriveDecoder(derivation.renaming.snakeCase)
    implicit val interactionResponseDecoder: Decoder[GetInteractionsResponse] = deriveDecoder(derivation.renaming.snakeCase)

    override def getAllInteractions(page: Int, pageSize: Int): EitherT[Future, DeskComApiError, List[Interaction]] = {
      for {
        httpResponse <- httpClient.request(
          HttpRequest(
            "GET",
            s"${config.baseUrl}/api/v2/interactions?page=$page&per_page=$pageSize&sort_field=created_at&sort_direction=asc",
            List(authHeader)
          )
        ).leftMap(httpError => DeskComApiError(s"Request for interactions failed: $httpError"))
        _ <- EitherT.fromEither(validateStatusCode(httpResponse.statusCode))
        parsedResponseBody <- EitherT(Future.successful(parseGetAllInteractions(httpResponse.body)))
      } yield  parsedResponseBody._embedded.entries
    }

    private def parseGetAllInteractions(body: String): Either[DeskComApiError, GetInteractionsResponse] = {
      decode[GetInteractionsResponse](body)
        .left
        .map(parsingFailure => DeskComApiError(s"Failed to parse interaction response: ${parsingFailure}"))
    }

    private def validateStatusCode(statusCode: Int): Either[DeskComApiError, Unit] = {
      statusCode match {
        case 200 => Right(())
        case statusCode => Left(DeskComApiError(s"Interactions endpoint returned status: $statusCode"))
      }
    }
  }
}

case class Interaction(id: Int, createdAt: String, updatedAt: String, body: String, from: String, to: String,
                       cc: String, bcc: String, direction: String, status: String, subject: String)

case class GetInteractionsEmbedded(entries: List[Interaction])

case class GetInteractionsResponse(_embedded: GetInteractionsEmbedded)

case class DeskComApiConfig(baseUrl: String, username: String, password: String)

case class DeskComApiError(message: String)