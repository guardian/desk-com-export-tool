package com.gu.deskcomexporttool

import cats.data.EitherT

import scala.concurrent.Future

trait Exporter {
  def export(config: ExportConfig): EitherT[Future, ExporterError, Unit]
}

object Exporter {
  def apply(): Exporter = new Exporter() {
    override def export(config: ExportConfig): EitherT[Future, ExporterError, Unit] = ???
  }
}

case class ExporterError()
