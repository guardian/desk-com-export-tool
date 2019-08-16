package com.gu.deskcomexporttool

import scala.concurrent.Await
import scala.concurrent.duration._

object DeskComExportToolApp extends App {
  System.exit(Await.result(CommandLineRunner(Exporter()).run(args), 1 hour))
}