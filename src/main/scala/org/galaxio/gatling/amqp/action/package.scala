package org.galaxio.gatling.amqp

import com.typesafe.scalalogging.StrictLogging
import org.galaxio.gatling.amqp.request.AmqpProtocolMessage

package object action {

  trait AmqpLogging extends StrictLogging {
    def logMessage(text: => String, msg: AmqpProtocolMessage): Unit = {
      logger.debug(text)
      logger.trace(msg.toString)
    }
  }

  sealed trait Dest
  case class DirectDest(exchName: String, rk: String) extends Dest
  case class QueueDest(qName: String)                 extends Dest
}
