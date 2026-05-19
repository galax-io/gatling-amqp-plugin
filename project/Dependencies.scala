import sbt.*

object Dependencies {
  val gatlingVersion = "3.13.5"

  lazy val gatlingCore: Seq[ModuleID] = Seq(
    "io.gatling" % "gatling-core"      % gatlingVersion % Provided,
    "io.gatling" % "gatling-core-java" % gatlingVersion % Provided,
  )

  lazy val gatling: Seq[ModuleID] = Seq(
    "io.gatling.highcharts" % "gatling-charts-highcharts" % gatlingVersion % "it,test",
    "io.gatling"            % "gatling-test-framework"    % gatlingVersion % "it,test",
  )

  lazy val rabbitmq    = "com.rabbitmq"       % "amqp-client"   % "5.30.0"
  lazy val commonsPool = "org.apache.commons" % "commons-pool2" % "2.13.1"
  lazy val fastUUID    = "com.eatthepath"     % "fast-uuid"     % "0.2.0"

  lazy val scalaTest               = "org.scalatest" %% "scalatest"                      % "3.2.19" % Test
  lazy val testcontainersScalatest = "com.dimafeng"  %% "testcontainers-scala-scalatest" % "0.44.1" % Test
  lazy val testcontainersRabbitmq  = "com.dimafeng"  %% "testcontainers-scala-rabbitmq"  % "0.44.1" % Test

}
