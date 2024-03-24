package io.cosmospf.gatling.amqp.protocol

import io.cosmospf.gatling.amqp.client.{AmqpConnectionPool, TrackerPool}
import io.gatling.core.protocol.ProtocolComponents
import io.gatling.core.session.Session

case class AmqpComponents(
    protocol: AmqpProtocol,
    connectionPublishPool: AmqpConnectionPool,
    connectionReplyPool: AmqpConnectionPool,
    trackerPool: TrackerPool,
) extends ProtocolComponents {
  override def onStart: Session => Session = Session.Identity

  override def onExit: Session => Unit = ProtocolComponents.NoopOnExit

}
