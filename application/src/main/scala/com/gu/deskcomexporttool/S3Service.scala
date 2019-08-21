package com.gu.deskcomexporttool

import org.slf4j.LoggerFactory

import scala.concurrent.ExecutionContext

trait S3Service {
  def open(location: String): Either[S3Error, S3InteractionsWriter]
}

object S3Service {
  val log = LoggerFactory.getLogger(this.getClass)

  def apply()(implicit ec: ExecutionContext): S3Service = new S3Service() {
    def open(location: String): Either[S3Error, S3InteractionsWriter] = {
      for {
        binaryWriter <- S3BinaryWriter("")
        interactionsWriter <- S3InteractionsWriter(binaryWriter)
      } yield interactionsWriter
    }
  }
}

case class S3Error(message: String)