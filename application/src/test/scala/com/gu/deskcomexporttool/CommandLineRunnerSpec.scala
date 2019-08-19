package com.gu.deskcomexporttool

import cats.data.EitherT
import cats.instances.future._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{FlatSpec, MustMatchers}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class CommandLineRunnerSpec extends FlatSpec with ScalaFutures with MustMatchers {
  "CommandLineRunnerSpec" must "parse command line and run feed" in {
    var config: Option[ExportConfig] = None
    val mockExporter = new Exporter {
      override def export(c: ExportConfig): EitherT[Future, ExporterError, Unit] = {
        config = Some(c)
        EitherT.rightT(())
      }
    }

    CommandLineRunner(mockExporter).run(Array("-f", "50")).futureValue mustBe 0

    config must equal(Some(ExportConfig(fetchSize = 50)))
  }
  it must "return error code if error is returned" in {
    val mockExporter = new Exporter {
      override def export(c: ExportConfig): EitherT[Future, ExporterError, Unit] = {
        EitherT.leftT(ExporterError())
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
