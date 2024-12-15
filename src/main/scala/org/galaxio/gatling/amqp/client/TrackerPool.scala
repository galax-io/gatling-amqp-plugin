package org.galaxio.gatling.amqp.client

import com.rabbitmq.client.Delivery
import io.gatling.commons.util.Clock
import io.gatling.core.actor.ActorSystem
import io.gatling.core.stats.StatsEngine
import io.gatling.core.util.NameGen
import org.galaxio.gatling.amqp.action.AmqpLogging
import org.galaxio.gatling.amqp.client.AmqpMessageTrackerActor.MessageConsumed
import org.galaxio.gatling.amqp.protocol.AmqpMessageMatcher
import org.galaxio.gatling.amqp.request.AmqpProtocolMessage

import java.util.concurrent.ConcurrentHashMap

class TrackerPool(
    pool: AmqpConnectionPool,
    system: ActorSystem,
    statsEngine: StatsEngine,
    clock: Clock,
) extends AmqpLogging with NameGen {

  private val trackers = new ConcurrentHashMap[String, AmqpMessageTracker]

  def tracker(
      sourceQueue: String,
      listenerThreadCount: Int,
      messageMatcher: AmqpMessageMatcher,
      responseTransformer: Option[AmqpProtocolMessage => AmqpProtocolMessage],
  ): AmqpMessageTracker =
    trackers.computeIfAbsent(
      sourceQueue,
      _ => {
        val actor =
          system.actorOf(new AmqpMessageTrackerActor(genName("amqpTrackerActor"), statsEngine, clock))

        for (_ <- 1 to listenerThreadCount) {
          val consumerChannel = pool.createConsumerChannel
          consumerChannel.basicConsume(
            sourceQueue,
            true,
            (_: String, message: Delivery) => {
              val receivedTimestamp = clock.nowMillis
              val amqpMessage       = AmqpProtocolMessage(message.getProperties, message.getBody)
              val replyId           = messageMatcher.responseMatchId(amqpMessage)
              logMessage(
                s"Message received AmqpMessageID=${message.getProperties.getMessageId} matchId=$replyId",
                amqpMessage,
              )
              actor ! MessageConsumed(
                replyId,
                receivedTimestamp,
                responseTransformer.map(_(amqpMessage)).getOrElse(amqpMessage),
              )
            },
            (_: String) => (),
          )
        }

        new AmqpMessageTracker(actor)
      },
    )
}
