package org.galaxio.gatling.amqp.client

import io.gatling.core.session.Session
import org.galaxio.gatling.amqp.protocol.AmqpComponents
import org.galaxio.gatling.amqp.request._

class AmqpPublisher(destination: AmqpExchange, components: AmqpComponents) extends WithAmqpChannel {

  private val awaitConfirm = components.protocol.publisherConfirms

  def publish(message: AmqpProtocolMessage, session: Session): Unit = {

    destination match {
      case AmqpDirectExchange(name, routingKey, _) =>
        for {
          exName <- name(session)
          exKey  <- routingKey(session)
        } withChannel { channel =>
          channel.basicPublish(exName, exKey, message.amqpProperties, message.payload)
          if (awaitConfirm) channel.waitForConfirmsOrDie(5000)
        }

      case AmqpQueueExchange(name, _) =>
        name(session).foreach(qName =>
          withChannel { channel =>
            channel.basicPublish("", qName, message.amqpProperties, message.payload)
            if (awaitConfirm) channel.waitForConfirmsOrDie(5000)
          },
        )

      case AmqpTopicExchange(name, routingKey, _) =>
        for {
          exName <- name(session)
          exKey  <- routingKey(session)
        } withChannel { channel =>
          channel.basicPublish(exName, exKey, message.amqpProperties, message.payload)
          if (awaitConfirm) channel.waitForConfirmsOrDie(5000)
        }
    }
  }

  override protected val pool: AmqpConnectionPool = components.connectionPublishPool
}
