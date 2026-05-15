package org.galaxio.gatling.amqp.request

import io.gatling.core.action.builder.ActionBuilder
import io.gatling.core.session.Expression
import org.galaxio.gatling.amqp.AmqpCheck
import org.galaxio.gatling.amqp.action.ConsumeBuilder
import io.gatling.core.config.GatlingConfiguration

case class ConsumeDslBuilderQueue(requestName: Expression[String], configuration: GatlingConfiguration) {
  def queue(name: Expression[String]): ConsumeDslBuilder =
    ConsumeDslBuilder(ConsumeAttributes(requestName, name), configuration)
}

case class ConsumeDslBuilder(attributes: ConsumeAttributes, configuration: GatlingConfiguration) {
  def timeout(millis: Long): ConsumeDslBuilder = copy(attributes = attributes.copy(timeout = millis))

  def check(checks: AmqpCheck*): ConsumeDslBuilder =
    copy(attributes = attributes.copy(checks = attributes.checks ::: checks.toList))

  def silent: ConsumeDslBuilder = copy(attributes = attributes.copy(silent = true))

  def build(): ActionBuilder = ConsumeBuilder(attributes, configuration)
}
