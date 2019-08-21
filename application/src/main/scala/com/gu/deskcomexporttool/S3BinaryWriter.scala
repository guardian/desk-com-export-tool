package com.gu.deskcomexporttool

import java.io.OutputStream

import alex.mojaki.s3upload.StreamTransferManager
import cats.syntax.either._
import com.amazonaws.auth.profile.ProfileCredentialsProvider
import com.amazonaws.services.s3.AmazonS3ClientBuilder

trait S3BinaryWriter {
  def outputStream(): OutputStream

  def close(): Unit
}

object S3BinaryWriter {
  def apply(location: String): Either[S3Error, S3BinaryWriter] = {
    Either.catchNonFatal {
      val awsClient = AmazonS3ClientBuilder.standard()
        .withCredentials(new ProfileCredentialsProvider("membership"))
        .build()

      val streamTransferManager = new StreamTransferManager("ophan-raw-deskdotcom-cases", "/test/interactions.csv", awsClient)

      val stream = streamTransferManager.getMultiPartOutputStreams.get(0)

      new S3BinaryWriter() {
        override def outputStream(): OutputStream = stream

        override def close(): Unit = {
          streamTransferManager.complete()
        }
      }
    }.left.map { ex =>
      S3Error(s"Failed to create connection to s3: $ex")
    }
  }
}