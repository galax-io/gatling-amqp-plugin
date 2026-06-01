package org.galaxio.gatling.amqp.client

import com.rabbitmq.client.{AlreadyClosedException, Channel, ShutdownSignalException}
import io.gatling.commons.validation.{Failure, Success, Validation}
import io.gatling.core.session.Session
import org.galaxio.gatling.amqp.protocol.AmqpComponents
import org.galaxio.gatling.amqp.request._

object AmqpPublisher {
  private val ConfirmTimeoutMillis: Long = 5000L
}

class AmqpPublisher(destination: AmqpExchange, components: AmqpComponents) {
  import AmqpPublisher._

  private val awaitConfirm: Boolean    = components.protocol.publisherConfirms
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

  private def resolveDestination(session: Session): Validation[(String, String)] = destination match {
    case AmqpDirectExchange(name, routingKey, _) =>
      for {
        exName <- name(session)
        exKey  <- routingKey(session)
      } yield (exName, exKey)
    case AmqpTopicExchange(name, routingKey, _)  =>
      for {
        exName <- name(session)
        exKey  <- routingKey(session)
      } yield (exName, exKey)
    case AmqpQueueExchange(name, _)              =>
      name(session).map(("", _))
  }

  private def doPublish(exchange: String, routingKey: String, message: AmqpProtocolMessage): Unit =
    withChannel { ch =>
      ch.basicPublish(exchange, routingKey, message.amqpProperties, message.payload)
      if (awaitConfirm) ch.waitForConfirmsOrDie(ConfirmTimeoutMillis)
    }

  def publish(message: AmqpProtocolMessage, session: Session): Unit =
    resolveDestination(session) match {
      case Success((exchange, routingKey)) => doPublish(exchange, routingKey, message)
      case Failure(errorMessage)           =>
        throw new IllegalStateException(s"Failed to resolve AMQP destination: $errorMessage")
    }
}
