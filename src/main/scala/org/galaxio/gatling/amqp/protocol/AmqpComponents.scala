package org.galaxio.gatling.amqp.protocol

import org.galaxio.gatling.amqp.client.{AmqpConnectionPool, TrackerPool}
import io.gatling.core.protocol.ProtocolComponents
import io.gatling.core.session.Session
import org.galaxio.gatling.amqp.client.{AmqpConnectionPool, TrackerPool}

case class AmqpComponents(
    protocol: AmqpProtocol,
    connectionPublishPool: AmqpConnectionPool,
    connectionReplyPool: AmqpConnectionPool,
    trackerPool: TrackerPool,
) extends ProtocolComponents {
  override def onStart: Session => Session = Session.Identity

  override def onExit: Session => Unit = ProtocolComponents.NoopOnExit

}
