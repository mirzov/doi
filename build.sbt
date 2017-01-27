lazy val commonSettings = Seq(
	organization := "se.lu.nateko.cp",
	scalaVersion := "2.11.8"
)

lazy val commonJvmSettings = Seq(
	scalacOptions ++= Seq(
		"-unchecked",
		"-deprecation",
		"-Xlint",
		"-Ywarn-dead-code",
		"-language:_",
		"-target:jvm-1.8",
		"-encoding", "UTF-8"
	)
) ++ commonSettings

val common = crossProject
	.crossType(CrossType.Pure)
	.in(file("common"))
	.settings(
		name := "doi-common",
		version := "0.1.0-SNAPSHOT",
		libraryDependencies += "org.scalatest" %%% "scalatest" % "3.0.1" % "test"
	)
	.jsSettings(commonSettings: _*)
	.jvmSettings(commonJvmSettings: _*)
	.jsSettings(name := "doi-common-js")
	.jvmSettings(name := "doi-common-jvm")

lazy val commonJs = common.js
lazy val commonJvm = common.jvm

lazy val core = project
	.in(file("core"))
	.dependsOn(commonJvm)
	.settings(commonJvmSettings: _*)
	.enablePlugins(SbtTwirl)
	.settings(
		name := "doi-core",
		version := "0.1.0-SNAPSHOT",
		libraryDependencies += "org.scalatest" %% "scalatest" % "3.0.1" % "test",
		publishTo := {
			val nexus = "https://repo.icos-cp.eu/content/repositories/"
			if (isSnapshot.value)
				Some("snapshots" at nexus + "snapshots")
			else
				Some("releases"  at nexus + "releases")
		},
		credentials += Credentials(Path.userHome / ".ivy2" / ".credentials")
	)

lazy val app = crossProject
	.in(file("."))
	.settings(
		name := "doi",
		libraryDependencies ++= Seq(
			"com.lihaoyi" %%% "upickle" % "0.4.4"
		)
	)
	.jsSettings(commonSettings: _*)
	.jvmSettings(commonJvmSettings: _*)
	.jsSettings(
		name := "doi-js",
		libraryDependencies ++= Seq(
			"com.lihaoyi" %%% "scalatags" % "0.6.2"
		)
	)
	.jvmSettings(
		name := "doi-jvm",
		libraryDependencies ++= Seq(
			"com.typesafe.akka" %% "akka-http"   % "10.0.3",
			"se.lu.nateko.cp"   %% "views-core"  % "0.2-SNAPSHOT",
			"se.lu.nateko.cp"   %% "cpauth-core" % "0.5-SNAPSHOT"
		),
		baseDirectory in reStart := {
			baseDirectory.in(reStart).value.getParentFile
		},
		assemblyMergeStrategy.in(assembly) := {
			case PathList(name) if(name.endsWith("-fastopt.js")) =>
				MergeStrategy.discard
			case x =>
				val originalStrategy = assemblyMergeStrategy.in(assembly).value
				originalStrategy(x)
		}
	)
	.jsConfigure(_.dependsOn(commonJs))
	.jvmConfigure(_.dependsOn(core).enablePlugins(SbtTwirl))

lazy val appJs = app.js
lazy val appJvm = app.jvm
	.settings(
		resources.in(Compile) += fastOptJS.in(appJs, Compile).value.data,
		watchSources ++= watchSources.in(appJs, Compile).value,
		assembledMappings.in(assembly) := {
			val finalJsFile = fullOptJS.in(appJs, Compile).value.data
			assembledMappings.in(assembly).value :+ sbtassembly.MappingSet(None, Vector((finalJsFile, finalJsFile.getName)))
		}
	)

