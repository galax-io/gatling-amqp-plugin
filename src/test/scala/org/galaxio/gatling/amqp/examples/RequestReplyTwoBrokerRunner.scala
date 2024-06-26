package org.galaxio.gatling.amqp.examples

import io.gatling.app.Gatling
import io.gatling.shared.cli.GatlingCliOptions

/** This object simply provides a `main` method that wraps [[io.gatling.app.Gatling]].main, which allows us to do some
  * configuration and setup before Gatling launches.
  */
object RequestReplyTwoBrokerRunner {

  def main(args: Array[String]): Unit = {
    // This sets the class for the simulation we want to run.
    val simulationClass = classOf[RequestReplyTwoBrokerExample].getName

    Gatling.main(
      args ++
        Array(
          GatlingCliOptions.Simulation.shortOption,
          simulationClass,
          GatlingCliOptions.ResultsFolder.shortOption,
          "results",
          GatlingCliOptions.Launcher.shortOption,
          "sbt",
        ),
    )
  }
}
