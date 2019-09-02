package com.gu.deskcomexporttool

import java.io.{BufferedWriter, OutputStreamWriter}
import java.time.{Instant, ZoneOffset}
import java.time.format.DateTimeFormatter

import cats.syntax.either._
import org.apache.commons.csv.{CSVFormat, CSVPrinter, QuoteMode}
import org.slf4j.LoggerFactory


trait S3InteractionsWriter {
  def write(interaction: Interaction): Either[S3Error, Unit]

  def close(): Unit
}

object S3InteractionsWriter {
  private val log = LoggerFactory.getLogger(this.getClass)

  def apply(s3BinaryWriter: S3BinaryWriter, scrubSensitiveData: Boolean): Either[S3Error, S3InteractionsWriter] = {
    Either.catchNonFatal {
      CSVFormat
        .DEFAULT
        .withQuoteMode(QuoteMode.ALL)
        .withRecordSeparator("\n")
        .withHeader(
          "ParentCaseDeskId",
          "Desk_Case_Number__c",
          "CreatedDate",
          "LastModifiedDate",
          "TextBody",
          "FromAddress",
          "ToAddress",
          "CcAddress",
          "BccAddress",
          "IsIncoming",
          "Status",
          "Subject"
        )
        .print(new BufferedWriter(new OutputStreamWriter(s3BinaryWriter.outputStream(), "UTF-8")))
    }.bimap(
      { ex =>
        S3Error(s"Failed to write headers: $ex")
      },
      { printer =>
        new S3InteractionsWriter() {
          override def write(interaction: Interaction): Either[S3Error, Unit] = {
            log.trace(s"Writing interaction: $interaction")
            for {
              caseId <- parseSelfLink(interaction._links.self)
              mappedStatus = createStatusMapping(interaction.status)
              mappedDirection = createDirectionMapping(interaction.direction)
              createdDate <- parseDate(interaction.createdAt)
              updatedDate <- parseUpdatedDate(interaction.updatedAt, createdDate)
              writeInteractionResult <- writeInteraction(
                printer,
                interaction,
                caseId,
                scrubSensitiveData,
                mappedStatus,
                mappedDirection,
                createdDate,
                updatedDate
              )
            } yield writeInteractionResult
          }

          override def close(): Unit = {
            printer.close()
            s3BinaryWriter.close()
          }

          private val SelfLinkRegex = """/api/v2/cases/(.*?)/.*""".r

          private def parseSelfLink(link: Link): Either[S3Error, String] = {
            link.href match {
              case SelfLinkRegex(caseId) => Right(caseId)
              case _ => Left(S3Error(s"Failed to parse Interaction self link: ${link.href}"))
            }
          }

          private def createStatusMapping(status: Option[String]): Option[Int] = {
            status match {
              case Some("received") => Some(1)
              case Some("pending") => Some(3)
              case Some("sent") => Some(3)
              case _ => None
            }
          }

          private def createDirectionMapping(direction: Option[String]) : String = {
            direction match {
              case Some("in") => "TRUE"
              case Some("out") => "FALSE"
              case _ => ""
            }
          }

          private def parseDate(date: String): Either[S3Error, Instant] = {
            Either.catchNonFatal {
              Instant.from(DateTimeFormatter.ISO_DATE_TIME.parse(date))
            }.leftMap(error => S3Error(s"Failed to parse date: $date error:$error"))
          }

          private def sanitizeUpdatedDate(createdDate: Instant, updatedDate: Instant) = {
            if(updatedDate.isBefore(createdDate)) {
              createdDate
            } else {
              updatedDate
            }
          }

          private def parseUpdatedDate(updatedDate: String, createdDate: Instant) = {
            parseDate(updatedDate).map(sanitizeUpdatedDate(createdDate, _))
          }

          private val csvDateFormat = DateTimeFormatter.ISO_DATE_TIME.withZone(ZoneOffset.UTC)
          private def writeInteraction(printer: CSVPrinter, interaction: Interaction, caseId: String,
                                       scrubSensitiveData: Boolean, mappedStatus: Option[Int], mappedDirection: String,
                                       createdDate: Instant, updatedDate: Instant) = {
            Either.catchNonFatal {
              mappedStatus.fold
                {
                  log.debug(s"Excluded interaction for case:$caseId as it had status of :${interaction.status}")
                  ()
                }
                { status: Int =>
                  printer.printRecord(
                    caseId,
                    caseId,
                    csvDateFormat.format(createdDate),
                    csvDateFormat.format(updatedDate),
                    scrubString(interaction.body.getOrElse(""), scrubSensitiveData),
                    scrubString(interaction.from.getOrElse(""), scrubSensitiveData),
                    scrubString(interaction.to.getOrElse(""), scrubSensitiveData),
                    scrubString(interaction.cc.getOrElse(""), scrubSensitiveData),
                    scrubString(interaction.bcc.getOrElse(""), scrubSensitiveData),
                    mappedDirection,
                    status.toString,
                    scrubString(interaction.subject.getOrElse(""), scrubSensitiveData)
                  )
                }

            }.leftMap(ex => S3Error(s"Failed to write headers: $ex"))
          }

          private def scrubString(string: String, scrub: Boolean) = {
            if (scrub) {
              new String(Array.fill(string.length)('x'))
            } else {
              string
            }
          }
        }
      }
    )
  }
}