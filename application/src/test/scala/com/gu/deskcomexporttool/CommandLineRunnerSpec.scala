package com.gu.deskcomexporttool

import cats.data.EitherT
import cats.instances.future._
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.{FlatSpec, MustMatchers}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class CommandLineRunnerSpec extends FlatSpec with ScalaFutures with MustMatchers with IntegrationPatience {
  "CommandLineRunnerSpec" must "parse command line and run feed" in {
    var config: Option[ExportConfig] = None
    val mockExporter = new Exporter {
      override def export(c: ExportConfig): EitherT[Future, ExporterError, Unit] = {
        config = Some(c)
        EitherT.rightT(())
      }
    }

    CommandLineRunner(mockExporter).run(Array(
      "-f", "50", "-u", "aUsername", "-p", "aPassword", "-o", "aprofile", "-s", "s3://bucket/path"
    )).futureValue mustBe 0

    config must equal(Some(
      ExportConfig(
        pageSize = 50,
        DeskComApiConfig("https://guardianuserhelp.desk.com", "aUsername", "aPassword"),
        S3Config("s3://bucket/path", "aprofile", scrub = true)
      )
    ))
  }
  it must "return error code if error is returned" in {
    val mockExporter = new Exporter {
      override def export(c: ExportConfig): EitherT[Future, ExporterError, Unit] = {
        EitherT.leftT(ExporterExportFailure(""))
      }
    }

    CommandLineRunner(mockExporter).run(Array()).futureValue mustBe -1
  }
  it must "return error code if exception is thrown" in {
    val mockExporter = new Exporter {
      override def export(c: ExportConfig): EitherT[Future, ExporterError, Unit] = {
        EitherT.liftF(Future.failed(new Exception()))
      }
    }

    CommandLineRunner(mockExporter).run(Array()).futureValue mustBe -1
  }
}
