package se.lu.nateko.cp.doi.core

import java.net.URL
import scala.concurrent.Future
import scala.concurrent.ExecutionContext
import scala.io.Source
import se.lu.nateko.cp.doi.DoiMeta
import se.lu.nateko.cp.doi.Doi
import scala.util.Try
import scala.collection.Seq
import play.api.libs.json._
import se.lu.nateko.cp.doi.JsonSupport._

class DoiClient(config: DoiClientConfig, http: DoiHttp)(implicit ctxt: ExecutionContext) {

	val metaBase: URL = new URL(config.restEndpoint, "dois")
	val clientDois: URL = new URL(s"${config.restEndpoint}dois?client-id=${config.symbol.toLowerCase()}&page[size]=500")

	def doi(suffix: String): Doi = Doi(config.doiPrefix, suffix)
	def metaUrl(doi: Doi) = new URL(s"$metaBase/$doi")

	def listDoisMeta: Future[String] = {
		http.getJson(clientDois).map(response => response.body)
	}

	def getMetadata(doi: Doi): Future[String] = http.getJson(metaUrl(doi)).map(response => response.body)

	def putMetadata(meta: DoiMeta): Future[Unit] = {
		http.putPayload(metaUrl(meta.doi), Json.obj("data" -> Json.obj("attributes" -> meta)).toString(), "application/vnd.api+json").flatMap(analyzeResponse{
			case 200 | 201 => Future.successful(())
		})
	}

	def delete(doi: Doi): Future[Unit] =
		http.delete(metaUrl(doi)).flatMap(
			analyzeResponse{case 204 => Future.successful(())}
		)

	private def analyzeResponse[T](pf: PartialFunction[Int, Future[T]])(resp: http.DoiResponse): Future[T] = {
		pf.applyOrElse(
			resp.status,
			(status: Int) => {
				val msg = s"""Problem communicating with DateCite:
					|HTTP status code: $status
					|Response message: ${resp.message}
					|Response content: ${resp.body}""".stripMargin
				Future.failed(new Exception(msg))
			}
		)
	}
}
