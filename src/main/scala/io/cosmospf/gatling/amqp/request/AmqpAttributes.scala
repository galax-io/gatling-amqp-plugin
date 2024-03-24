package io.cosmospf.gatling.amqp.request

import io.gatling.core.session.Expression
import io.cosmospf.gatling.amqp.AmqpCheck

case class AmqpAttributes(
    requestName: Expression[String],
    destination: AmqpExchange,
    selector: Option[String],
    message: AmqpMessage,
    messageProperties: AmqpMessageProperties = AmqpMessageProperties(),
    checks: List[AmqpCheck] = Nil,
)
