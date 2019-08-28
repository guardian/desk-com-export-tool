package com.gu.deskcomexporttool

import java.io.{ByteArrayOutputStream, OutputStream, StringReader}

import org.apache.commons.csv.CSVFormat
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{FlatSpec, Inside, MustMatchers}
import scala.collection.JavaConverters._

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
          "\"case_id\",\"created_at\",\"updated_at\",\"body\",\"from\",\"to\",\"cc\",\"bcc\",\"direction\"," +
            "\"status\",\"subject\"\n" +
            "\"c11111\",\"2018-01-01T01:01:01Z\",\"2019-01-01T01:01:01Z\",\"test body 1111\"," +
            "\"Test User 1111 <testuser1111@test.com>\",\"<toaddress1111@test.com>\",\"<ccaddress1111@test.com>\"," +
            "\"<bccaddress1111@test.com>\",\"TRUE\",\"1\",\"Test Subject 1111\"\n"
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
          "\"case_id\",\"created_at\",\"updated_at\",\"body\",\"from\",\"to\",\"cc\",\"bcc\",\"direction\"," +
            "\"status\",\"subject\"\n" +
            "\"c11111\",\"2018-01-01T01:01:01Z\",\"2019-01-01T01:01:01Z\",\"xxxxxxxxxxxxxx\"," +
            "\"xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx\",\"xxxxxxxxxxxxxxxxxxxxxxxx\",\"xxxxxxxxxxxxxxxxxxxxxxxx\"," +
            "\"xxxxxxxxxxxxxxxxxxxxxxxxx\",\"TRUE\",\"1\",\"xxxxxxxxxxxxxxxxx\"\n"
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
              .get("status") must equal(statusCode.toString)
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
          records.get(0).get("direction") must equal(csvDirection)
      }
    }

    testDirectionMapping("in", "TRUE")
    testDirectionMapping("out", "FALSE")
    testDirectionMapping("", "")
  }

  private def writeInteractionAndParseResults(interaction: Interaction) = {

    val writtenData = new ByteArrayOutputStream()
    val mockBinaryWriter = new S3BinaryWriter {
      override def outputStream(): OutputStream = writtenData

      override def close(): Unit = ()
    }

    S3InteractionsWriter(mockBinaryWriter, scrubSensitiveData = true).map { interactionWriter =>
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
