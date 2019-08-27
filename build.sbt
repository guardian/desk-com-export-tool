name := "desk-com-export-tool"

version := "1.0-SNAPSHOT"

lazy val application = (project in file("application"))
  .settings(
    libraryDependencies ++= Seq(
      "ch.qos.logback" % "logback-classic" % "1.2.3",
      "com.amazonaws" % "aws-java-sdk-s3" % "1.11.613",
      "com.github.alexmojaki" % "s3-stream-upload" % "2.0.0",
      "com.github.scopt" %% "scopt" % "3.7.1",
      "com.softwaremill.sttp" %% "async-http-client-backend-future" % "1.6.4",
      "com.softwaremill.sttp" %% "core" % "1.6.4",
      "io.circe" %% "circe-parser" % "0.11.1",
      "io.circe" %% "circe-derivation" % "0.11.0-M2",
      "org.apache.commons" % "commons-csv" % "1.7",
      "org.typelevel" %% "cats-core" % "1.6.1",
    ),
    libraryDependencies ++= Seq(
      "org.scalatest" %% "scalatest" % "3.0.8" % Test,
    ),
    scalacOptions ++= Seq(
      "-feature",
      "-language:postfixOps"
    ),
    mainClass in assembly := Some("com.gu.deskcomexporttool.DeskComExportToolApp"),
    assemblyMergeStrategy in assembly := {
      case PathList("META-INF", "MANIFEST.MF")  => MergeStrategy.discard
      case _ => MergeStrategy.first
    },
    addCompilerPlugin("org.typelevel" % "kind-projector" % "0.10.3" cross CrossVersion.binary),
  )