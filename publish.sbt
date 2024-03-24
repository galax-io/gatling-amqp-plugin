ThisBuild / organization := "io.cosmospf"

ThisBuild / scmInfo := Some(
  ScmInfo(
    url("https://github.com/cosmospf/gatling-amqp-plugin"),
    "git@github.com:cosmospf/gatling-amqp-plugin.git",
  ),
)

ThisBuild / description := "Plugin for support performance testing with AMQP in Gatling"
ThisBuild / licenses    := List("Apache 2" -> new URL("http://www.apache.org/licenses/LICENSE-2.0.txt"))
ThisBuild / homepage    := Some(url("https://github.com/cosmospf/gatling-amqp-plugin"))
