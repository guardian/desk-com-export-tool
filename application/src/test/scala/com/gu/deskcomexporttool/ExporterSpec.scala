package com.gu.deskcomexporttool

import cats.data.EitherT
import cats.instances.future._
import com.gu.deskcomexporttool.InteractionFixture.interactionWithId
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.{FlatSpec, MustMatchers}

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class ExporterSpec extends FlatSpec with ScalaFutures with MustMatchers with IntegrationPatience {
  "Exporter" must "write all interactions to s3" in {
    val interaction1: Interaction = interactionWithId(1)
    val interaction2: Interaction = interactionWithId(2)
    val interaction3: Interaction = interactionWithId(3)
    val invalidInteraction: Interaction = interactionWithId(4)

    val mockInteractionsFetcher = createMockFetcher(interaction1, interaction2, interaction3, invalidInteraction)

    val (writtenValidInteractions, mockValidS3Writer: S3InteractionsWriter) = createMockWriter
    val (writtenInvalidInteractions, mockInvalidS3Writer: S3InteractionsWriter) = createMockWriter

    val validInteractions = List(interaction1, interaction2, interaction3)
    val mockInteractionValidator = createMockValidator(validInteractions)

    Exporter(
      s3Service(mockValidS3Writer, mockInvalidS3Writer),
      fetcherFactory(mockInteractionsFetcher),
      mockInteractionValidator
    ).export(ExportConfig(123, DeskComApiConfig("", "", ""))).value.futureValue must equal(Right(()))

    writtenValidInteractions.toArray must contain only(interaction1, interaction2, interaction3)
    writtenInvalidInteractions.toArray must contain only (invalidInteraction)
  }

  private def createMockValidator(validInteractions: List[Interaction]): InteractionValidator = {
    new InteractionValidator {
      override def isValid(interaction: Interaction): Boolean = validInteractions.contains(interaction)
    }
  }

  private def createMockFetcher(
    interaction1: Interaction,
    interaction2: Interaction,
    interaction3: Interaction,
    invalidInteraction: Interaction
  ): InteractionFetcher = {
    val mockInteractionsFetcher = new InteractionFetcher {

      var responses = Map(
        "0" -> Right(Interactions(List(interaction1, interaction2), "batch2")),
        "batch2" -> Right(Interactions(List(interaction3, invalidInteraction), "batch3")),
        "batch3" -> Left(InteractionFetcherNoMoreInteractions())
      )

      override def getInteractions(sinceId: String): EitherT[Future, InteractionFetcherError, Interactions] =
        EitherT.fromEither(responses(sinceId))

      override def close(): Unit = ()
    }
    mockInteractionsFetcher
  }

  private def createMockWriter: (ArrayBuffer[Interaction], S3InteractionsWriter) = {
    val writtenValidInteractions = ArrayBuffer[Interaction]()

    val mockValidS3Writer = new S3InteractionsWriter {
      override def write(interaction: Interaction): Either[S3Error, Unit] = {
        writtenValidInteractions.append(interaction)
        Right(())
      }

      override def close(): Unit = ()
    }
    (writtenValidInteractions, mockValidS3Writer)
  }

  private def s3Service(
    mockValidS3Writer: S3InteractionsWriter,
    mockInvalidS3Writer: S3InteractionsWriter
  ): S3Service = {
    val mockS3Service = new S3Service {
      override def open(location: String, config: S3Config): Either[S3Error, S3InteractionsWriter] = {
        Right(
          location match {
            case "s3://ophan-raw-deskdotcom-cases/interactions.csv" => mockValidS3Writer
            case "s3://ophan-raw-deskdotcom-cases/interactions-invalid.csv" => mockInvalidS3Writer
          }
        )
      }
    }
    mockS3Service
  }

  private def fetcherFactory(mockInteractionsFetcher: InteractionFetcher) = {
    val mockInteractionsFetcherFactory = new InteractionFetcherFactory {
      override def create(config: DeskComApiConfig, pageSize: Int): InteractionFetcher = mockInteractionsFetcher
    }
    mockInteractionsFetcherFactory
  }
}
