package se.lu.nateko.cp.doi

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import se.lu.nateko.cp.doi.core.DoiClientConfig
import java.net.URL
import se.lu.nateko.cp.cpauth.core.PublicAuthConfig
import se.lu.nateko.cp.cpauth.core.UserId
import scala.jdk.CollectionConverters.ListHasAsScala
import scala.collection.Seq

case class DoiConfig(
	client: DoiClientConfig,
	prefixInfo: String,
	auth: PublicAuthConfig,
	admins: Seq[UserId]
)

object DoiConfig {

	def getConfig: DoiConfig = {
		val allConf = getAppConfig
		val doiConf = allConf.getConfig("cpdoi")
		DoiConfig(
			client = getClientConfig(doiConf),
			prefixInfo = doiConf.getString("prefix"),
			auth = getAuthConfig(allConf),
			admins = allConf.getStringList("cpdoi.admins").asScala.map(UserId(_))
		)
	}

	private def getAppConfig: Config = {
		val default = ConfigFactory.load
		val confFile = new java.io.File("application.conf").getAbsoluteFile
		if(!confFile.exists) default
		else ConfigFactory.parseFile(confFile).withFallback(default)
	}

	private def getClientConfig(doiConf: Config) = DoiClientConfig(
		symbol = doiConf.getString("symbol"),
		password = doiConf.getString("password"),
		restEndpoint = new URL(doiConf.getString("restEndpoint")),
		mdsEndpoint = new URL(doiConf.getString("mdsEndpoint")),
		doiPrefix = doiConf.getString("prefix")
	)

	private def getAuthConfig(allConf: Config): PublicAuthConfig = {
		val auth = allConf.getConfig("cpauthAuthPub")
		PublicAuthConfig(
			authCookieName = auth.getString("authCookieName"),
			authCookieDomain = auth.getString("authCookieDomain"),
			authHost = auth.getString("authHost"),
			publicKeyPath = auth.getString("publicKeyPath")
		)
	}
}