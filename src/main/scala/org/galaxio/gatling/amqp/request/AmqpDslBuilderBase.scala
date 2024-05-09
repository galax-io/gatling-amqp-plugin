package org.galaxio.gatling.amqp.request

import io.gatling.core.config.GatlingConfiguration
import io.gatling.core.session._

case class AmqpDslBuilderBase(requestName: Expression[String]) {
  def publish(implicit configuration: GatlingConfiguration): PublishDslBuilderExchange           =
    PublishDslBuilderExchange(requestName, configuration)
  def requestReply(implicit configuration: GatlingConfiguration): RequestReplyDslBuilderExchange =
    RequestReplyDslBuilderExchange(requestName, configuration)
}
