package io.cosmospf.gatling

import io.gatling.core.check.Check
import io.cosmospf.gatling.amqp.request.AmqpProtocolMessage

package object amqp {
  type AmqpCheck = Check[AmqpProtocolMessage]
}
