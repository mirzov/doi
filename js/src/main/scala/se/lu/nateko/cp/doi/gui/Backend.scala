package se.lu.nateko.cp.doi.gui

import scala.concurrent.Future
import org.scalajs.dom.ext.Ajax
import scalajs.concurrent.JSExecutionContext.Implicits.queue
import play.api.libs.json._
import se.lu.nateko.cp.doi.JsonSupport._
import se.lu.nateko.cp.doi.Doi
import se.lu.nateko.cp.doi.DoiMeta
import se.lu.nateko.cp.doi.PrefixInfo
import org.scalajs.dom.ext.AjaxException
import org.scalajs.dom.raw.XMLHttpRequest

object Backend {

	private def parseTo[T : Reads](xhr: XMLHttpRequest): T = {
		Json.parse(xhr.responseText).as[T]
	}

	def getPrefixInfo: Future[PrefixInfo] = Ajax
		.get("/api/doiprefix")
		.recoverWith(recovery("fetch DOI prefix"))
		.map(parseTo[PrefixInfo])

	def getDoiList: Future[Seq[Doi]] = Ajax
		.get("/api/list")
		.recoverWith(recovery("fetch DOI list"))
		.map(parseTo[Seq[Doi]])

	def checkIfExists(doi: Doi): Future[Boolean] = Ajax
		.get(s"/api/$doi/exists")
		.recoverWith(recovery("check for DOI existence"))
		.map(_.responseText.toBoolean)

	def getTarget(doi: Doi): Future[Option[String]] = Ajax
		.get(s"/api/$doi/target")
		.recoverWith(recovery("fetch DOI target URL"))
		.map(_.responseText)
		.map(s => if(s.isEmpty) None else Some(s))

	def getMeta(doi: Doi): Future[DoiMeta] = Ajax
		.get(s"/api/$doi/metadata")
		.recoverWith(recovery("fetch DOI metadata"))
		.map(parseTo[DoiMeta])

	def getInfo(doi: Doi): Future[DoiInfo] = Backend.getMeta(doi)
		.zip(Backend.getTarget(doi))
		.map{
			case (meta, target) => DoiInfo(meta, target, true)
		}

	def updateUrl(doi: Doi, url: String) = Ajax
		.post(s"/api/$doi/target", url)
		.recoverWith(recovery("update the target URL"))

	def updateMeta(meta: DoiMeta) = Ajax
		.post("/api/metadata", Json.toJson(meta).toString)
		.recoverWith(recovery("update DOI metadata"))

	private def recovery(hint: String): PartialFunction[Throwable, Future[XMLHttpRequest]] = {
		case AjaxException(xhr) =>
			val msg = if(xhr.responseText.isEmpty)
				s"Got HTTP status ${xhr.status} when trying to $hint"
			else s"Error when trying to $hint:\n" + xhr.responseText

			Future.failed(new Exception(msg))
	}
}
