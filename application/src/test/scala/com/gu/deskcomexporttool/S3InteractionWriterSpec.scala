package com.gu.deskcomexporttool

import java.io.{ByteArrayOutputStream, OutputStream}

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{FlatSpec, Inside, MustMatchers}

class S3InteractionWriterSpec extends FlatSpec with ScalaFutures with MustMatchers {
  "S3InteractionWriter" must "format interaction as csv" in {
    val writtenData = new ByteArrayOutputStream()
    val mockBinaryWriter = new S3BinaryWriter {
      override def outputStream(): OutputStream = writtenData

      override def close(): Unit = ()
    }

    Inside.inside(S3InteractionsWriter(mockBinaryWriter)) {
      case Right(interactionWriter) =>
        interactionWriter.write(InteractionFixture.interaction) must equal(Right(()))

        interactionWriter.close()

        new String(writtenData.toByteArray, "UTF-8") must equal(
          "\"case_id\",\"created_at\",\"updated_at\",\"body\",\"from\",\"to\",\"cc\",\"bcc\",\"direction\"," +
            "\"status\",\"subject\"\n" +
            "\"todo\",\"2018-01-01T01:01:01Z\",\"2019-01-01T01:01:01Z\",\"test body 1111\"," +
            "\"Test User 1111 <testuser1111@test.com>\",\"<toaddress1111@test.com>\",\"<ccaddress1111@test.com>\"," +
            "\"<bccaddress1111@test.com>\",\"in\",\"received\",\"Test Subject 1111\"\n"
        )
    }
  }
}
