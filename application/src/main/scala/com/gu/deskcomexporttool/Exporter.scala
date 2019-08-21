package com.gu.deskcomexporttool

import cats.data.EitherT
import cats.instances.future._

import scala.collection.immutable
import scala.concurrent.{ExecutionContext, Future}

trait Exporter {
  def export(config: ExportConfig): EitherT[Future, ExporterError, Unit]
}

object Exporter {
  def apply(s3Service: S3Service, deskComClientFactory: DeskComClientFactory)(implicit ec: ExecutionContext): Exporter = new Exporter() {
    override def export(config: ExportConfig): EitherT[Future, ExporterError, Unit] = {
      val deskComClient = deskComClientFactory.createClient(config.deskComApiConfig)

      for {
        s3Writer <- EitherT
          .fromEither(s3Service.open(config.s3Config))
          .leftMap(error => ExporterError(s"Failed to create s3 writer: $error"))
        res <- writeInteractions(deskComClient, s3Writer)
      } yield res
    }

    private def writeInteractions(deskComClient: DeskComClient, s3Writer: S3InteractionsWriter) = {
      val result = deskComClient
        .getAllInteractions(1, 1)
        .map { interactions: immutable.Seq[Interaction] =>
          interactions.foreach(interaction => s3Writer.write(interaction))
        }
        .leftMap(apiError => ExporterError(s"Export failed: $apiError"))

      result.value.onComplete { _ =>
        s3Writer.close()
        deskComClient.close()
      }

      result
    }
  }
}

case class ExporterError(message: String)
