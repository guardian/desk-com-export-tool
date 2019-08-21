package com.gu.deskcomexporttool

import java.net.URI

import cats.data.EitherT
import cats.instances.future._
import com.softwaremill.sttp._
import com.softwaremill.sttp.asynchttpclient.future.AsyncHttpClientFutureBackend

import scala.concurrent.{ExecutionContext, Future}

trait HttpClient {
  def request(request: HttpRequest): EitherT[Future, HttpError, HttpResponse]

  def close(): Unit
}

object HttpClient {
  def apply()(implicit ec: ExecutionContext): HttpClient = new HttpClient() {
    private implicit val backend: SttpBackend[Future, Nothing] = AsyncHttpClientFutureBackend()

    override def request(request: HttpRequest): EitherT[Future, HttpError, HttpResponse] = {
      println(request.url)

      val sttpRequest = sttp.copy[Id, String, Nothing](
        uri = Uri(new URI(request.url)),
        method = Method(request.method),
        headers = request.headers.map(header => (header.key, header.value)))

      for {
        sttpResponse <- EitherT.right(sttpRequest.send())
        body <- EitherT.fromEither(sttpResponse.body).leftMap(HttpError)
      } yield HttpResponse(sttpResponse.code, body)
    }

    override def close(): Unit = backend.close()
  }
}

case class HttpResponse(statusCode: Int, body: String)

case class HttpRequest(method: String, url: String, headers: List[HttpHeader])

case class HttpHeader(key: String, value: String)

case class HttpError(message: String)