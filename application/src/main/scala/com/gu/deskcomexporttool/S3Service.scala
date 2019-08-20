package com.gu.deskcomexporttool

import cats.data.EitherT
import cats.instances.future._
import org.slf4j.LoggerFactory

import scala.concurrent.{ExecutionContext, Future}

trait S3Service {
  def open(location: String): S3Writer
}

object S3Service {
  val log = LoggerFactory.getLogger(this.getClass)

  def apply()(implicit ec: ExecutionContext): S3Service = new S3Service() {
    def open(location: String): S3Writer = new S3Writer {
      override def write(interaction: Interaction): EitherT[Future, S3Error, Unit] = {
        log.info(s"Interaction id: ${interaction.id}")
        EitherT.rightT(())
      }

      override def close(): Unit = ()
    }
  }
}

trait S3Writer {
  def write(bytes: Interaction): EitherT[Future, S3Error, Unit]
  def close(): Unit
}

case class S3Error(message:String)