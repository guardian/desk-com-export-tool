package com.gu.deskcomexporttool

import cats.data.EitherT
import cats.instances.either._
import cats.instances.future._
import cats.instances.list._
import cats.syntax.either._
import cats.syntax.traverse._
import org.slf4j.LoggerFactory

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

trait Exporter {
  def export(config: ExportConfig): EitherT[Future, ExporterError, Unit]
}

object Exporter {
  private val log = LoggerFactory.getLogger(this.getClass)

  def apply(s3Service: S3Service, interactionFetcherFactory: InteractionFetcherFactory)(implicit ec: ExecutionContext): Exporter = new Exporter() {
    override def export(config: ExportConfig): EitherT[Future, ExporterError, Unit] = {
      val deskComClient = interactionFetcherFactory.create(config.deskComApiConfig, config.pageSize)

      for {
        s3Writer <- EitherT
          .fromEither[Future](s3Service.open(config.s3Config))
          .leftMap(error => ExporterExportFailure(s"Failed to create s3 writer: $error"))
        _ <- writeInteractionsAndCleanup(deskComClient, s3Writer)
      } yield ()
    }

    private def writeInteractionsAndCleanup(interactionFetcher: InteractionFetcher, s3Writer: S3InteractionsWriter) = {
      val result = writeInteractions(interactionFetcher, "0", s3Writer, 0).recover {
        case ExporterExportComplete() => ()
      }

      EitherT(
        result.value.transform { result =>
          Try {
            s3Writer.close()
            interactionFetcher.close()
          }.recover {
            case ex => log.error(s"Failed to shutdown gracefully: $ex")
          }
          result
        }
      )
    }

    private def writeInteractions(interactionFetcher: InteractionFetcher, sinceId: String, s3Writer: S3InteractionsWriter, interactionCount: Long): EitherT[Future, ExporterError, Unit] = {
      for {
        batchInfo <- writeInteractionsBatch(interactionFetcher, sinceId, s3Writer)
        totalInteractions = interactionCount + batchInfo.interactionCount
        _ = log.debug(s"Written $totalInteractions interactions")
        _ <- writeInteractions(interactionFetcher, batchInfo.nextBatchId, s3Writer, totalInteractions)
      } yield ()
    }

    private case class InteractionBatchInfo(nextBatchId: String, interactionCount: Int)

    private def writeInteractionsBatch(interactionFetcher: InteractionFetcher, sinceId: String, s3Writer: S3InteractionsWriter) = {
      for {
        interactions <- interactionFetcher
          .getInteractions(sinceId)
          .leftMap[ExporterError] {
            case _: InteractionFetcherNoMoreInteractions => ExporterExportComplete()
            case unexpectedError: InteractionFetcherUnexpectedError => ExporterExportFailure(s"Export failed: $unexpectedError")
          }
        _ <- EitherT.fromEither[Future] {
          interactions
            .interactions
            .traverse[Either[S3Error, ?], Unit](interaction => s3Writer.write(interaction))
            .leftMap[ExporterError](apiError => ExporterExportFailure(s"Export failed: $apiError"))
        }
      } yield InteractionBatchInfo(interactions.nextBatchSinceId, interactions.interactions.size)
    }
  }
}

sealed trait ExporterError

case class ExporterExportFailure(message: String) extends ExporterError

case class ExporterExportComplete() extends ExporterError
