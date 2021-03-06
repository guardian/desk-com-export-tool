package com.gu.deskcomexporttool

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

object DeskComExportToolApp extends App {
  System.exit(Await.result(CommandLineRunner(Exporter(S3Service(), InteractionFetcherFactory(), InteractionValidator())).run(args), Duration.Inf))
}