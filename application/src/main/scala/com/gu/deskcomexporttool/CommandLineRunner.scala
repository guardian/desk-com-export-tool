package com.gu.deskcomexporttool

import org.slf4j.LoggerFactory
import scopt.OptionParser

import scala.concurrent.Future

trait CommandLineRunner {
  def run(args: Array[String]): Future[Int]
}

case class ExportConfig(fetchSize: Int = 100)

object CommandLineRunner {
  private val log = LoggerFactory.getLogger(classOf[CommandLineRunner])

  def apply(exporter: Exporter): CommandLineRunner = {
    new CommandLineRunner {
      override def run(args: Array[String]): Future[Int] = {
        import scala.concurrent.ExecutionContext.Implicits.global
        commandLineParser
          .parse(args, ExportConfig())
          .fold[Future[Int]](returnFailure)(
            f => runExport(f).value
              .map { exportResult =>
                exportResult.fold(
                  { exportError =>
                    log.info(s"Export failed $exportError")
                    FAILURE_CODE
                  },
                  { _ =>
                    log.info("Export complete")
                    SUCCESS_CODE
                  })
              }
              .recover { case e =>
                log.error("Export failed unexpectedly", e)
                FAILURE_CODE
              }
          )
      }

      private val commandLineParser = new OptionParser[ExportConfig]("desk-com-export-tool") {
        opt[Int]('f', "fetchsize")
          .action((x, c) => c.copy(fetchSize = x))
          .text("number of items fetched from desk.com per api call")
      }

      private def returnFailure = Future.successful(FAILURE_CODE)

      private def runExport(config: ExportConfig) = exporter.export(config)
    }
  }

  private val SUCCESS_CODE = 0
  private val FAILURE_CODE = -1
}