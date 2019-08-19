package com.gu.deskcomexporttool

import cats.data.EitherT

import scala.concurrent.Future

trait S3Service {
  def open(location: String): S3File
}

object S3Service {
  def apply(): S3Service = new S3Service() {
    def open(location: String): S3File = ???
  }
}

trait S3File {
  def write(bytes: Array[Byte]): EitherT[Future, S3Error, Unit]
  def close(): EitherT[Future, S3Error, Unit]
}

case class S3Error(message:String)