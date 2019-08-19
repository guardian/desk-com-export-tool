package com.gu.deskcomexporttool

import cats.data.EitherT

import scala.concurrent.Future

trait HttpClient {
  def request(request: HttpRequest): EitherT[Future, HttpError, HttpResponse]
}

object HttpClient {
  def apply(): HttpClient = new HttpClient() {
    override def request(request: HttpRequest): EitherT[Future, HttpError, HttpResponse] = ???
  }
}

case class HttpResponse(statusCode: Int, body: String)

case class HttpRequest(method: String, url: String, headers: List[HttpHeader])

case class HttpHeader(key: String, value: String)

case class HttpError(message: String)