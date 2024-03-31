package org.galaxio.gatling

import io.gatling.core.check.Check
import org.galaxio.gatling.amqp.request.AmqpProtocolMessage

package object amqp {
  type AmqpCheck = Check[AmqpProtocolMessage]
}
