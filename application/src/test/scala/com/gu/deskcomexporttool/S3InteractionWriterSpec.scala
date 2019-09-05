package com.gu.deskcomexporttool

import java.io.{ByteArrayOutputStream, OutputStream, StringReader}
import java.time.{Instant, ZoneOffset}
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

import org.apache.commons.csv.CSVFormat
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{FlatSpec, Inside, MustMatchers}

class S3InteractionWriterSpec extends FlatSpec with ScalaFutures with MustMatchers {
  "S3InteractionWriter" must "format interaction as csv" in {
    val writtenData = new ByteArrayOutputStream()
    val mockBinaryWriter = new S3BinaryWriter {
      override def outputStream(): OutputStream = writtenData

      override def close(): Unit = ()
    }

    Inside.inside(S3InteractionsWriter(mockBinaryWriter, scrubSensitiveData = false)) {
      case Right(interactionWriter) =>
        interactionWriter.write(InteractionFixture.interaction) must equal(Right(()))

        interactionWriter.close()

        new String(writtenData.toByteArray, "UTF-8") must equal(
          "\"ParentCaseDeskId\",\"Desk_Case_Number__c\",\"CreatedDate\",\"LastModifiedDate\",\"TextBody\"," +
            "\"FromAddress\",\"ToAddress\",\"CcAddress\",\"BccAddress\",\"IsIncoming\",\"Status\",\"Subject\"\n" +
            "\"c11111\",\"c11111\",\"2018-01-01T01:01:01Z\",\"2019-01-01T01:01:01Z\",\"test body 1111\"," +
            "\"testuser1111@test.com\",\"toaddress1111@test.com\",\"ccaddress1111@test.com\"," +
            "\"bccaddress1111@test.com\",\"TRUE\",\"1\",\"Test Subject 1111\"\n"
        )
    }
  }
  it must "format scrub sensitive data" in {
    val writtenData = new ByteArrayOutputStream()
    val mockBinaryWriter = new S3BinaryWriter {
      override def outputStream(): OutputStream = writtenData

      override def close(): Unit = ()
    }

    Inside.inside(S3InteractionsWriter(mockBinaryWriter, scrubSensitiveData = true)) {
      case Right(interactionWriter) =>
        interactionWriter.write(InteractionFixture.interaction) must equal(Right(()))

        interactionWriter.close()

        new String(writtenData.toByteArray, "UTF-8") must equal(
          "\"ParentCaseDeskId\",\"Desk_Case_Number__c\",\"CreatedDate\",\"LastModifiedDate\",\"TextBody\"," +
            "\"FromAddress\",\"ToAddress\",\"CcAddress\",\"BccAddress\",\"IsIncoming\",\"Status\",\"Subject\"\n" +
            "\"c11111\",\"c11111\",\"2018-01-01T01:01:01Z\",\"2019-01-01T01:01:01Z\",\"xxxxxxxxxxxxxx\"," +
            "\"xxxxxxxxxxxxxxxxxxxxx\",\"xxxxxxxxxxxxxxxxxxxxxx\",\"xxxxxxxxxxxxxxxxxxxxxx\"," +
            "\"xxxxxxxxxxxxxxxxxxxxxxx\",\"TRUE\",\"1\",\"xxxxxxxxxxxxxxxxx\"\n"
        )
    }
  }
  it must "map status values to status codes" in {
    def testStatusCodeMapping(status: String, statusCodeOption: Option[Int]) = {
      Inside.inside(writeInteractionAndParseResults(InteractionFixture.interaction.copy(status = Some(status)))) {
        case Right(records) =>
          statusCodeOption.fold {
            records.size() must equal(0)
          } { statusCode =>
            records
              .get(0)
              .get("Status") must equal(statusCode.toString)
          }
      }
    }

    testStatusCodeMapping("received", Some(1))
    testStatusCodeMapping("pending", Some(3))
    testStatusCodeMapping("sent", Some(3))
    testStatusCodeMapping("draft", None)
    testStatusCodeMapping("failed", None)
  }
  it must "map status direction field" in {
    def testDirectionMapping(interactionDirection: String, csvDirection: String) = {
      Inside.inside(writeInteractionAndParseResults(InteractionFixture.interaction.copy(direction = Some(interactionDirection)))) {
        case Right(records) =>
          records.get(0).get("IsIncoming") must equal(csvDirection)
      }
    }

    testDirectionMapping("in", "TRUE")
    testDirectionMapping("out", "FALSE")
    testDirectionMapping("", "")
  }
  it must "format email addresses correctly" in {
    def testDirectionMapping(interactionDirection: String, csvDirection: String) = {
      val deskEmailFormat = "Name 1<email1@test.com>,Name 2<email2@test.com>,email3@test.com,"

      val csvEmailFormat = "email1@test.com;email2@test.com;email3@test.com"
      Inside.inside(writeInteractionAndParseResults(
        InteractionFixture.interaction.copy(
          to = Some(deskEmailFormat),
          from = Some(deskEmailFormat),
          cc = Some(deskEmailFormat),
          bcc = Some(deskEmailFormat)
        ))) {
        case Right(records) =>
          records.get(0).get("FromAddress") must equal(csvEmailFormat)
          records.get(0).get("ToAddress") must equal(csvEmailFormat)
          records.get(0).get("CcAddress") must equal(csvEmailFormat)
          records.get(0).get("BccAddress") must equal(csvEmailFormat)
      }
    }

    testDirectionMapping("in", "TRUE")
    testDirectionMapping("out", "FALSE")
    testDirectionMapping("", "")
  }
  it must "remove invalid email addresses" in {
    def testDirectionMapping(interactionDirection: String, csvDirection: String) = {
      val deskEmailFormat = "Name 1<not email>,Name 2<asdasd>,email3attest.com,twitter"

      Inside.inside(writeInteractionAndParseResults(
        InteractionFixture.interaction.copy(
          to = Some(deskEmailFormat),
          from = Some(deskEmailFormat),
          cc = Some(deskEmailFormat),
          bcc = Some(deskEmailFormat)
        ))) {
        case Right(records) =>
          records.get(0).get("FromAddress") must equal("")
          records.get(0).get("ToAddress") must equal("")
          records.get(0).get("CcAddress") must equal("")
          records.get(0).get("BccAddress") must equal("")
      }
    }

    testDirectionMapping("in", "TRUE")
    testDirectionMapping("out", "FALSE")
    testDirectionMapping("", "")
  }
  it must "ensure updated date is on or after created date" in {
    val createdDate = Instant.now()
    val updatedDate = createdDate.minus(1, ChronoUnit.SECONDS)

    val dateTimeFormat = DateTimeFormatter.ISO_DATE_TIME.withZone(ZoneOffset.UTC)

    Inside.inside(writeInteractionAndParseResults(
      InteractionFixture.interaction.copy(
        createdAt = dateTimeFormat.format(createdDate),
        updatedAt = dateTimeFormat.format(updatedDate)
      )
    )) {
      case Right(records) =>
        records.get(0).get("LastModifiedDate") must equal(dateTimeFormat.format(createdDate))
    }
  }

  private def writeInteractionAndParseResults(interaction: Interaction) = {

    val writtenData = new ByteArrayOutputStream()
    val mockBinaryWriter = new S3BinaryWriter {
      override def outputStream(): OutputStream = writtenData

      override def close(): Unit = ()
    }

    S3InteractionsWriter(mockBinaryWriter, scrubSensitiveData = false).map { interactionWriter =>
      interactionWriter.write(interaction) must equal(Right(()))

      interactionWriter.close()

      CSVFormat
        .DEFAULT
        .withFirstRecordAsHeader()
        .parse(new StringReader(writtenData.toString("UTF-8")))
        .getRecords
    }

  }
}
