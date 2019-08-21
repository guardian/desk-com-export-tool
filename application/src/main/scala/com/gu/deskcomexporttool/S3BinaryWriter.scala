package com.gu.deskcomexporttool

import java.io.OutputStream

import alex.mojaki.s3upload.StreamTransferManager
import cats.syntax.either._
import com.amazonaws.auth.profile.ProfileCredentialsProvider
import com.amazonaws.services.s3.{AmazonS3, AmazonS3ClientBuilder}

trait S3BinaryWriter {
  def outputStream(): OutputStream

  def close(): Unit
}

object S3BinaryWriter {
  def apply(location: String, profile: String): Either[S3Error, S3BinaryWriter] = {
    for {
      parsedLocation <- parseS3Location(location)
      writer <- createWriter(parsedLocation, profile)
    } yield writer
  }

  private def createWriter(s3Location: S3Location, profile: String) = {
    for {
      awsClient <- createAwsClient(profile)
      transferManager <- createTransferManager(s3Location, awsClient)
      stream <- openStream(transferManager, awsClient)
    } yield stream
  }

  private def createTransferManager(s3Location: S3Location, awsClient: AmazonS3) = {
    Either.catchNonFatal {
      new StreamTransferManager(s3Location.bucket, s3Location.path, awsClient)
    }.leftMap { ex =>
      S3Error(s"Failed to create s3 transfer manager: $ex")
    }
  }

  private def createAwsClient(profile: String) = {
    Either.catchNonFatal {
      AmazonS3ClientBuilder.standard()
        .withCredentials(new ProfileCredentialsProvider(profile))
        .build()
    }.leftMap { ex =>
      S3Error(s"Failed to create aws client: $ex")
    }
  }

  private def openStream(streamTransferManager: StreamTransferManager, awsClient: AmazonS3) = {
      Either.catchNonFatal {
        val stream = streamTransferManager.getMultiPartOutputStreams.get(0)

        new S3BinaryWriter() {
          override def outputStream(): OutputStream = stream

          override def close(): Unit = {
            streamTransferManager.complete()
            awsClient.shutdown()
          }
        }
      }.leftMap { ex =>
        S3Error(s"Failed to open connection to s3: $ex")
      }
  }

  private val S3LocationRegex = """s3://(.*?)/(.*)""".r

  private def parseS3Location(location: String) = location match {
    case S3LocationRegex(bucket, path) => Right(S3Location(bucket, path))
    case _ => Left(S3Error(s"Invalid s3 location: $location"))
  }
}

case class S3Location(bucket: String, path: String)