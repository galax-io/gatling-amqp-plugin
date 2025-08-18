import Dependencies.*

lazy val root = (project in file("."))
  .enablePlugins(GitVersioning, GatlingPlugin)
  .settings(
    name         := "gatling-amqp-plugin",
    scalaVersion := "2.13.16",
    // Do not publish Gatling/GatlingIt configuration artifacts (prevents enterprisePackage on CI)
    Gatling / publishArtifact   := false,
    GatlingIt / publishArtifact := false,
    libraryDependencies ++= gatling ++ gatlingCore,
    libraryDependencies ++= Seq(rabbitmq, commonsPool, fastUUID),
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
