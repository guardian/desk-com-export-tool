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

  def apply(s3Service: S3Service, deskComClientFactory: DeskComClientFactory)(implicit ec: ExecutionContext): Exporter = new Exporter() {
    override def export(config: ExportConfig): EitherT[Future, ExporterError, Unit] = {
      val deskComClient = deskComClientFactory.createClient(config.deskComApiConfig)

      for {
        s3Writer <- EitherT
          .fromEither[Future](s3Service.open(config.s3Config))
          .leftMap(error => ExporterError(s"Failed to create s3 writer: $error"))
        _ <- writeInteractions(deskComClient, s3Writer, config.fetchSize)
      } yield ()
    }

    private def writeInteractions(deskComClient: DeskComClient, s3Writer: S3InteractionsWriter, pageSize: Int) = {
      val result =
        for {
          interactions <- deskComClient
            .getAllInteractions(1, pageSize)
            .leftMap(apiError => ExporterError(s"Export failed: $apiError"))
          _ <- EitherT.fromEither[Future] {
            interactions
              .traverse[Either[S3Error, ?], Unit](interaction => s3Writer.write(interaction))
              .leftMap(apiError => ExporterError(s"Export failed: $apiError"))
          }
        } yield ()

      EitherT(
        result.value.transform { result =>
          Try {
            s3Writer.close()
            deskComClient.close()
          }.recover {
            case ex => log.error(s"Failed to shutdown gracefully: $ex")
          }
          result
        }
      )
    }
  }
}

case class ExporterError(message: String)
