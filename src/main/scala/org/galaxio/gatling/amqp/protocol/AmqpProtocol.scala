package org.galaxio.gatling.amqp.protocol

import com.rabbitmq.client.{Channel, ConnectionFactory}
import org.galaxio.gatling.amqp.client.{AmqpConnectionPool, TrackerPool}
import org.galaxio.gatling.amqp.request.AmqpProtocolMessage
import io.gatling.core.CoreComponents
import io.gatling.core.config.GatlingConfiguration
import io.gatling.core.protocol.{Protocol, ProtocolKey}

import scala.jdk.CollectionConverters._

object AmqpProtocol {
  val amqpProtocolKey: ProtocolKey[AmqpProtocol, AmqpComponents] = new ProtocolKey[AmqpProtocol, AmqpComponents] {
    override def protocolClass: Class[Protocol] = classOf[AmqpProtocol].asInstanceOf[Class[Protocol]]

    override def defaultProtocolValue(configuration: GatlingConfiguration): AmqpProtocol =
      throw new IllegalStateException("Can't provide a default value for AmqpProtocol")

    private def toJavaMap(map: Map[String, Any]): java.util.Map[String, Object] =
      map.asJava.asInstanceOf[java.util.Map[String, Object]]

    private def runInitAction(c: Channel): PartialFunction[AmqpChannelInitAction, Unit] = {
      case ExchangeDeclare(e)        =>
        c.exchangeDeclare(e.name, e.exchangeType, e.durable, e.autoDelete, toJavaMap(e.arguments))
      case QueueDeclare(q)           =>
        c.queueDeclare(q.name, q.durable, q.exclusive, q.autoDelete, toJavaMap(q.arguments))
      case BindQueue(q, e, rk, args) =>
        c.queueBind(q, e, rk, toJavaMap(args))

    }

    override def newComponents(coreComponents: CoreComponents): AmqpProtocol => AmqpComponents =
      amqpProtocol => {
        val requestPool = new AmqpConnectionPool(
          amqpProtocol.connectionFactory,
          amqpProtocol.consumersThreadCount,
          amqpProtocol.channelPoolSize,
          amqpProtocol.publisherConfirms,
        )
        coreComponents.actorSystem.registerOnTermination(requestPool.close())

        val replyPool = new AmqpConnectionPool(
          amqpProtocol.replyConnectionFactory,
          amqpProtocol.consumersThreadCount,
          amqpProtocol.channelPoolSize,
          publisherConfirms = false,
        )
        coreComponents.actorSystem.registerOnTermination(replyPool.close())

        if (amqpProtocol.initActions.nonEmpty) {
          val ch = requestPool.channel
          try amqpProtocol.initActions.foreach(runInitAction(ch))
          catch {
            case e: Exception =>
              requestPool.invalidate(ch)
              throw e
          }
          requestPool.returnChannel(ch)
        }

        if (amqpProtocol.replyInitActions.nonEmpty) {
          val ch = replyPool.channel
          try amqpProtocol.replyInitActions.foreach(runInitAction(ch))
          catch {
            case e: Exception =>
              replyPool.invalidate(ch)
              throw e
          }
          replyPool.returnChannel(ch)
        }

        val trackerPool = new TrackerPool(
          replyPool,
          coreComponents.actorSystem,
          coreComponents.statsEngine,
          coreComponents.clock,
        )
        coreComponents.actorSystem.registerOnTermination(trackerPool.close())

        AmqpComponents(amqpProtocol, requestPool, replyPool, trackerPool)
      }
  }
}

case class AmqpProtocol(
    connectionFactory: ConnectionFactory,
    replyConnectionFactory: ConnectionFactory,
    deliveryMode: DeliveryMode,
    replyTimeout: Option[Long],
    consumersThreadCount: Int,
    messageMatcher: AmqpMessageMatcher,
    responseTransformer: Option[AmqpProtocolMessage => AmqpProtocolMessage],
    initActions: AmqpChannelInitActions,
    replyInitActions: AmqpChannelInitActions = Nil,
    channelPoolSize: Int = 16,
    publisherConfirms: Boolean = false,
) extends Protocol {
  type Components = AmqpComponents
}
