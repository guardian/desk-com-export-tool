package com.gu.deskcomexporttool

import org.slf4j.LoggerFactory

import scala.concurrent.ExecutionContext

trait S3Service {
  def open(config: S3Config): Either[S3Error, S3InteractionsWriter]
}

object S3Service {
  private val log = LoggerFactory.getLogger(this.getClass)

  def apply()(implicit ec: ExecutionContext): S3Service = new S3Service() {
    def open(config: S3Config): Either[S3Error, S3InteractionsWriter] = {
      for {
        binaryWriter <- S3BinaryWriter(config.location, config.awsProfile)
        interactionsWriter <- S3InteractionsWriter(binaryWriter, config.scrub)
      } yield interactionsWriter
    }
  }
}

case class S3Error(message: String)

case class S3Config(location: String, awsProfile: String, scrub: Boolean)