package com.gu.deskcomexporttool

import java.net.URI

import cats.data.EitherT
import cats.instances.future._
import com.gu.deskcomexporttool.Logging.truncate
import com.softwaremill.sttp._
import com.softwaremill.sttp.asynchttpclient.future.AsyncHttpClientFutureBackend
import org.slf4j.LoggerFactory

import scala.concurrent.{ExecutionContext, Future}

trait HttpClient {
  def request(request: HttpRequest): EitherT[Future, HttpError, HttpResponse]

  def close(): Unit
}

object HttpClient {
  private val log = LoggerFactory.getLogger(this.getClass)

  def apply()(implicit ec: ExecutionContext): HttpClient = new HttpClient() {
    private implicit val backend: SttpBackend[Future, Nothing] = AsyncHttpClientFutureBackend()

    override def request(request: HttpRequest): EitherT[Future, HttpError, HttpResponse] = {
      log.debug(s"Calling desk.com api: ${request.url}")

      val sttpRequest = sttp.copy[Id, String, Nothing](
        uri = Uri(new URI(request.url)),
        method = Method(request.method),
        headers = request.headers.map(header => (header.key, header.value)))

      for {
        sttpResponse <- EitherT.right(sttpRequest.send())
        _ = log.debug(s"Received desk.com response status:${sttpResponse.code} " +
                      s"body:${truncate(sttpResponse.body.right.getOrElse(""))}")
        body = sttpResponse.body.fold(identity, identity)
      } yield HttpResponse(sttpResponse.code, body)
    }

    override def close(): Unit = backend.close()
  }
}

case class HttpResponse(statusCode: Int, body: String)

case class HttpRequest(method: String, url: String, headers: List[HttpHeader])

case class HttpHeader(key: String, value: String)

case class HttpError(message: String)