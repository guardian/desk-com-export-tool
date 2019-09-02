package com.gu.deskcomexporttool

import org.slf4j.LoggerFactory
import scopt.OptionParser

import scala.concurrent.{ExecutionContext, Future}

trait CommandLineRunner {
  def run(args: Array[String]): Future[Int]
}

case class ExportConfig(pageSize: Int = 100,
                        deskComApiConfig: DeskComApiConfig = DeskComApiConfig(baseUrl = "https://guardianuserhelp.desk.com",
                          username = "", password = ""),
                        validInteractionLocation: String = "s3://ophan-raw-deskdotcom-cases/interactions.csv",
                        invalidInteractionLocation: String = "s3://ophan-raw-deskdotcom-cases/interactions-invalid.csv",
                        s3Config: S3Config = S3Config("ophan", scrub = false))

object CommandLineRunner {
  private val log = LoggerFactory.getLogger(classOf[CommandLineRunner])

  def apply(exporter: Exporter)(implicit ec: ExecutionContext): CommandLineRunner = {
    new CommandLineRunner {
      override def run(args: Array[String]): Future[Int] = {
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
          .action((x, c) => c.copy(pageSize = x))
          .text("number of items fetched from desk.com per api call")
        opt[String]('u', "username")
          .action((x, c) => c.copy(deskComApiConfig = c.deskComApiConfig.copy(username = x)))
          .text("desk.com username")
        opt[String]('p', "password")
          .action((x, c) => c.copy(deskComApiConfig = c.deskComApiConfig.copy(password = x)))
          .text("desk.com password")
        opt[String]('o', "profile")
          .action((x, c) => c.copy(s3Config = c.s3Config.copy(awsProfile = x)))
          .text("aws profile name")
        opt[Unit]('s', "scrub")
          .action((_, c) => c.copy(s3Config = c.s3Config.copy(scrub = true)))
          .text("anonymise sensitive data")
        arg[String]("<s3 valid interactions location>...")
          .optional()
          .action((x, c) => c.copy(validInteractionLocation = x))
          .text("s3 valid interactions csv file location")
        arg[String]("<s3 invalid interactions location>...")
          .optional()
          .action((x, c) => c.copy(invalidInteractionLocation = x))
          .text("s3 invalid interactions csv file location")
      }

      private def returnFailure = Future.successful(FAILURE_CODE)

      private def runExport(config: ExportConfig) = exporter.export(config)
    }
  }

  private val SUCCESS_CODE = 0
  private val FAILURE_CODE = -1
}