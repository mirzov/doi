ThisBuild / scalaVersion := "3.2.0-RC1"
ThisBuild / organization := "se.lu.nateko.cp"

val commonSettings = Seq(
	scalacOptions ++= Seq(
		"-encoding", "UTF-8",
		"-unchecked",
		"-feature",
		"-deprecation"
	),
	libraryDependencies += "org.scalatest" %%% "scalatest" % "3.2.12" % "test" exclude("org.scala-lang.modules", "scala-xml_3")
)

val publishSettings = Seq(
	publishTo := {
		val nexus = "https://repo.icos-cp.eu/content/repositories/"
		if (isSnapshot.value)
			Some("snapshots" at nexus + "snapshots")
		else
			Some("releases"  at nexus + "releases")
	},
	credentials += Credentials(Path.userHome / ".ivy2" / ".credentials")
)

import sbtcrossproject.CrossPlugin.autoImport.{crossProject, CrossType}

//DataCite DOI metadata model, needed for back- and front end
val common = crossProject(JSPlatform, JVMPlatform)
	.crossType(CrossType.Pure)
	.in(file("common"))
	.settings(commonSettings)
	.settings(
		name := "doi-common",
		version := "0.2.1",
		cpDeploy := {
			sys.error("Please switch to project appJVM for deployment")
		}
	)
	.settings(publishSettings: _*)

//core functionality that may be reused by different apps (backends)
lazy val core = project
	.in(file("core"))
	.dependsOn(common.jvm)
	.settings(commonSettings ++ publishSettings: _*)
	.settings(
		name := "doi-core",
		libraryDependencies += "io.spray" %%  "spray-json" % "1.3.6" cross CrossVersion.for3Use2_13,
		libraryDependencies += "org.scala-lang.modules" %% "scala-xml" % "2.0.1" % "test",
		version := "0.2.1"
	)

//the DOI minting web app itself
lazy val app = crossProject(JSPlatform, JVMPlatform)
	.in(file("."))
	.settings(commonSettings)
	.settings(
		name := "doi",
		version := "0.2.1"
	)
	.jsSettings(
		name := "doi-js",
		libraryDependencies ++= Seq(
			"com.lihaoyi"       %%% "scalatags" % "0.11.1",
			"com.typesafe.play" %%% "play-json" % "2.10.0-RC6",
		),
		scalaJSUseMainModuleInitializer := true
	)
	.jvmSettings(
		name := "doi-jvm",

		libraryDependencies ++= Seq(
			"com.typesafe.akka" %% "akka-http-spray-json" % "10.2.9" cross CrossVersion.for3Use2_13,
			"com.typesafe.akka" %% "akka-stream"          % "2.6.19" cross CrossVersion.for3Use2_13,
			"com.typesafe.akka" %% "akka-slf4j"           % "2.6.19" cross CrossVersion.for3Use2_13,
			"ch.qos.logback"     % "logback-classic"      % "1.1.3",
			"com.sun.mail"       % "jakarta.mail"         % "1.6.7" exclude("com.sun.activation", "jakarta.activation"),
			"se.lu.nateko.cp"   %% "views-core"           % "0.5.4" cross CrossVersion.for3Use2_13,
			"se.lu.nateko.cp"   %% "cpauth-core"          % "0.6.5" cross CrossVersion.for3Use2_13,
		),
		reStart / baseDirectory  := {
			(reStart / baseDirectory).value.getParentFile
		},
		assembly / assemblyMergeStrategy := {
			case PathList(name) if(name.endsWith("-fastopt.js") || name.endsWith("module-info.class")) =>
				MergeStrategy.discard
			case x =>
				val originalStrategy = (assembly / assemblyMergeStrategy).value
				originalStrategy(x)
		}
	)
	.jsConfigure(_.dependsOn(common.js))
	.jvmConfigure(_.dependsOn(core))

lazy val appJs = app.js
lazy val appJvm = app.jvm
	.enablePlugins(IcosCpSbtDeployPlugin, SbtTwirl)
	.settings(
		cpDeployTarget := "doi",
		cpDeployBuildInfoPackage := "se.lu.nateko.cp.doi",

		libraryDependencies := {
			libraryDependencies.value.map{
				case m if m.name.startsWith("twirl-api") =>
					m.cross(CrossVersion.for3Use2_13)
				case m => m
			}
		},

		Compile / resources ++= {
			val jsFile = (appJs / Compile / fastOptJS).value.data
			val srcMap = new java.io.File(jsFile.getAbsolutePath + ".map")
			Seq(jsFile, srcMap)
		},

		watchSources ++= (appJs / Compile / watchSources).value,
		assembly / assembledMappings := {
			val finalJsFile = (appJs / Compile / fullOptJS).value.data
			(assembly / assembledMappings).value :+ sbtassembly.MappingSet(None, Vector((finalJsFile, finalJsFile.getName)))
		}
	)
