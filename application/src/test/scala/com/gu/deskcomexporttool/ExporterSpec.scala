package com.gu.deskcomexporttool

import cats.data.EitherT
import cats.instances.future._
import com.gu.deskcomexporttool.InteractionFixture.interactionWithId
import org.scalatest.{FlatSpec, MustMatchers}
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class ExporterSpec extends FlatSpec with ScalaFutures with MustMatchers with IntegrationPatience {
  "Exporter" must "write all interactions to s3" in {
    val interaction1: Interaction = interactionWithId(1)
    val interaction2: Interaction = interactionWithId(2)
    val interaction3: Interaction = interactionWithId(3)

    val mockInteractionsFetcher = new InteractionFetcher {

      var responses = Map(
        "0" -> Right(Interactions(List(interaction1, interaction2), "batch2")),
        "batch2" -> Right(Interactions(List(interaction3), "batch3")),
        "batch3" -> Left(InteractionFetcherNoMoreInteractions())
      )

      override def getInteractions(sinceId: String): EitherT[Future, InteractionFetcherError, Interactions] =
        EitherT.fromEither(responses(sinceId))

      override def close(): Unit = ()
    }

    val writtenInteractions = ArrayBuffer[Interaction]()

    val mockS3Writer = new S3InteractionsWriter {
      override def write(interaction: Interaction): Either[S3Error, Unit] = {
        writtenInteractions.append(interaction)
        Right(())
      }

      override def close(): Unit = ()
    }

    Exporter(s3Service(mockS3Writer), fetcherFactory(mockInteractionsFetcher))
      .export(ExportConfig(123, DeskComApiConfig("", "", ""))).value.futureValue must equal(Right(()))

    writtenInteractions.toArray must contain only (interaction1, interaction2, interaction3)
  }


  private def s3Service(mockS3Writer: S3InteractionsWriter) = {
    val mockS3Service = new S3Service {
      override def open(config: S3Config): Either[S3Error, S3InteractionsWriter] = Right(mockS3Writer)
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
