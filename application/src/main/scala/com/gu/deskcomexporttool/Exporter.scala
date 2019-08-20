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
      val s3Writer = s3Service.open("")
      val deskComClient = deskComClientFactory.createClient(config.deskComApiConfig)

      val result = deskComClient
        .getAllInteractions(1, 1)
        .map { interactions: immutable.Seq[Interaction] =>
          interactions.foreach(interaction => s3Writer.write(interaction))
        }
        .leftMap(apiError => ExporterError(s"Export failed: $apiError"))

      result.value.onComplete{ _ =>
        s3Writer.close()
        deskComClient.close()
      }

      result
    }
  }
}

case class ExporterError(message: String)
