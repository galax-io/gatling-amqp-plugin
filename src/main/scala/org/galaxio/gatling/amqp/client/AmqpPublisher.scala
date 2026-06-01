package org.galaxio.gatling.amqp.client

import com.rabbitmq.client.{AlreadyClosedException, Channel, ShutdownSignalException}
import io.gatling.core.session.Session
import org.galaxio.gatling.amqp.protocol.AmqpComponents
import org.galaxio.gatling.amqp.request._

class AmqpPublisher(destination: AmqpExchange, components: AmqpComponents) {

  private val awaitConfirm             = components.protocol.publisherConfirms
  private val pool: AmqpConnectionPool = components.connectionPublishPool

  private def withChannel[T](channelAction: Channel => T): T = {
    val ch = pool.channel
    try {
      val result = channelAction(ch)
      pool.returnChannel(ch)
      result
    } catch {
      case e @ (_: AlreadyClosedException | _: ShutdownSignalException) =>
        pool.invalidate(ch)
        throw e
      case e: Throwable                                                 =>
        pool.returnChannel(ch)
        throw e
    }
  }

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
}
