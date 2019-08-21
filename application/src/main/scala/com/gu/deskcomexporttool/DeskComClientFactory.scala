package com.gu.deskcomexporttool

import scala.concurrent.ExecutionContext

trait DeskComClientFactory {
  def createClient(config: DeskComApiConfig): DeskComClient
}

object DeskComClientFactory {
  def apply()(implicit ec: ExecutionContext): DeskComClientFactory = new DeskComClientFactory() {
    override def createClient(config: DeskComApiConfig): DeskComClient = DeskComClient(config, HttpClient())
  }
}