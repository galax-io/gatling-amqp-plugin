import Dependencies.*

lazy val root = (project in file("."))
  .enablePlugins(GatlingPlugin)
  .settings(
    name                        := "gatling-amqp-plugin",
    scalaVersion                := "2.13.18",
    // Do not publish Gatling/GatlingIt configuration artifacts (prevents enterprisePackage on CI)
    Gatling / publishArtifact   := false,
    GatlingIt / publishArtifact := false,
    // Binary-compatibility check against the latest published release.
    mimaPreviousArtifacts       := Set(organization.value %% name.value % "1.3.0"),
    libraryDependencies ++= gatling ++ gatlingCore,
    libraryDependencies ++= Seq(
      rabbitmq,
      commonsPool,
      fastUUID,
      scalaTest,
      testcontainersScalatest,
      testcontainersRabbitmq,
    ),
    scalacOptions ++= Seq(
      "-encoding",
      "utf8", // Option and arguments on same line
      "-deprecation",
      "-unchecked",
      "-language:implicitConversions",
      "-language:higherKinds",
      "-language:existentials",
      "-language:postfixOps",
    ),
  )
