name := "desk-com-export-tool"

version := "1.0-SNAPSHOT"

lazy val application = (project in file("application"))
  .settings(
    libraryDependencies ++= Seq(
      "ch.qos.logback" % "logback-classic" % "1.2.3",
      "com.github.scopt" %% "scopt" % "3.7.1",
      "io.circe" %% "circe-parser" % "0.11.1",
      "io.circe" %% "circe-derivation" % "0.11.0-M2",
      "org.typelevel" %% "cats-core" % "1.6.1",
    ),
    libraryDependencies ++= Seq(
      "org.scalatest" %% "scalatest" % "3.0.8" % Test,
    ),
    scalacOptions ++= Seq(
      "-feature",
      "-language:postfixOps"
    ),
  )