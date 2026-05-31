package org.galaxio.gatling.amqp.client

import com.rabbitmq.client.{Channel, Delivery}
import io.gatling.commons.util.Clock
import io.gatling.core.actor.ActorSystem
import io.gatling.core.stats.StatsEngine
import io.gatling.core.util.NameGen
import org.galaxio.gatling.amqp.action.AmqpLogging
import org.galaxio.gatling.amqp.client.AmqpMessageTrackerActor.MessageConsumed
import org.galaxio.gatling.amqp.protocol.AmqpMessageMatcher
import org.galaxio.gatling.amqp.request.AmqpProtocolMessage

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import scala.collection.mutable
import scala.util.Try

class TrackerPool(
    pool: AmqpConnectionPool,
    system: ActorSystem,
    statsEngine: StatsEngine,
    clock: Clock,
) extends AmqpLogging with NameGen {

  private val trackers        = new ConcurrentHashMap[String, AmqpMessageTracker]
  private val consumerEntries = new ConcurrentHashMap[String, mutable.ArrayBuffer[(Channel, String)]]
  private val pendingCounts   = new ConcurrentHashMap[String, AtomicInteger]

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

        val entries = mutable.ArrayBuffer.empty[(Channel, String)]

        for (_ <- 1 to listenerThreadCount) {
          val consumerChannel = pool.createConsumerChannel
          val consumerTag     = consumerChannel.basicConsume(
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
          entries += ((consumerChannel, consumerTag))
        }

        consumerEntries.put(sourceQueue, entries)
        new AmqpMessageTracker(actor)
      },
    )

  def incrementPending(sourceQueue: String): Unit =
    pendingCounts.computeIfAbsent(sourceQueue, _ => new AtomicInteger(0)).incrementAndGet()

  def decrementPendingAndEvict(sourceQueue: String): Unit = {
    val count = pendingCounts.get(sourceQueue)
    if (count != null && count.decrementAndGet() == 0) {
      removeTracker(sourceQueue)
    }
  }

  def removeTracker(sourceQueue: String): Unit = {
    trackers.remove(sourceQueue)
    pendingCounts.remove(sourceQueue)
    Option(consumerEntries.remove(sourceQueue)).foreach { entries =>
      entries.foreach { case (channel, tag) =>
        Try(channel.basicCancel(tag))
        Try(channel.close())
      }
    }
  }

  def close(): Unit = {
    import scala.jdk.CollectionConverters._
    consumerEntries.values().asScala.foreach { entries =>
      entries.foreach { case (channel, tag) =>
        Try(channel.basicCancel(tag))
        Try(channel.close())
      }
    }
    consumerEntries.clear()
    trackers.clear()
  }
}
