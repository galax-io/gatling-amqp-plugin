package org.galaxio.gatling.amqp.request

import io.gatling.core.session.Expression
import org.galaxio.gatling.amqp.AmqpCheck

case class ConsumeAttributes(
    requestName: Expression[String],
    queueName: Expression[String],
    timeout: Long = 5000,
    checks: List[AmqpCheck] = Nil,
    silent: Boolean = false,
)
