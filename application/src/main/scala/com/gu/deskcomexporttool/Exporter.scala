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
      val s3File = s3Service.open("")
      val deskComClient = deskComClientFactory.createClient(config.deskComApiConfig)

      deskComClient
        .getAllInteractions(1, 1)
        .map { interactions: immutable.Seq[Interaction] =>
          interactions.foreach(interaction => s3File.write(s"${interaction.id}\n".getBytes("UTF-8")))
        }
        .leftMap(apiError => ExporterError(s"Export failed: $apiError"))
    }
  }
}

case class ExporterError(message: String)
