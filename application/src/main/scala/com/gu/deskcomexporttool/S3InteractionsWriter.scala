package com.gu.deskcomexporttool

import java.io.{BufferedWriter, OutputStreamWriter}

import cats.syntax.either._
import org.apache.commons.csv.{CSVFormat, CSVPrinter, QuoteMode}
import org.slf4j.LoggerFactory


trait S3InteractionsWriter {
  def write(interaction: Interaction): Either[S3Error, Unit]

  def close(): Unit
}

object S3InteractionsWriter {
  private val log = LoggerFactory.getLogger(this.getClass)

  private val MaxBodyChars = 32759

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
              writeInteractionResult <- writeInteraction(
                printer,
                interaction,
                caseId,
                scrubSensitiveData,
                mappedStatus,
                mappedDirection
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

          private def writeInteraction(printer: CSVPrinter, interaction: Interaction, caseId: String,
                                       scrubSensitiveData: Boolean, mappedStatus: Option[Int], mappedDirection: String) = {
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
                    interaction.createdAt.getOrElse(""),
                    interaction.updatedAt.getOrElse(""),
                    scrubString(interaction.body.getOrElse("").take(MaxBodyChars), scrubSensitiveData),
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