package org.galaxio.gatling.amqp.action

import io.gatling.commons.stats.KO
import io.gatling.commons.util.Clock
import io.gatling.commons.validation.{Failure, Success, Validation}
import io.gatling.core.action.Action
import io.gatling.core.actor.ActorRef
import io.gatling.core.controller.throttle.Throttler
import io.gatling.core.session.Session
import io.gatling.core.stats.StatsEngine
import org.galaxio.gatling.amqp.client.AmqpPublisher
import org.galaxio.gatling.amqp.protocol.AmqpComponents
import org.galaxio.gatling.amqp.request.{AmqpAttributes, AmqpProtocolMessage, _}

class RequestReply(
    attributes: AmqpAttributes,
    replyDestination: AmqpExchange,
    components: AmqpComponents,
    val statsEngine: StatsEngine,
    val clock: Clock,
    val next: Action,
    throttler: Option[ActorRef[Throttler.Command]],
) extends AmqpAction(attributes, components, throttler) {

  private val replyTimeout = components.protocol.replyTimeout.getOrElse(0L)

  override val name: String = genName("amqpRequestReply")

  private def resolveDestination(dest: AmqpExchange, session: Session): Validation[String] =
    dest match {
      case AmqpDirectExchange(name, _, _) => name(session)
      case AmqpQueueExchange(name, _)     => name(session)
      case AmqpTopicExchange(name, _, _)  => name(session)
    }

  override protected def publishAndLogMessage(
      requestNameString: String,
      msg: AmqpProtocolMessage,
      session: Session,
      publisher: AmqpPublisher,
  ): Unit =
    resolveDestination(replyDestination, session) match {
      case Success(qName)        =>
        val trackerPool = components.trackerPool
        val tracker     = trackerPool.tracker(
          qName,
          components.protocol.consumersThreadCount,
          components.protocol.messageMatcher,
          components.protocol.responseTransformer,
        )
        trackerPool.incrementPending(qName)
        val id          = components.protocol.messageMatcher.requestMatchId(msg)
        val now         = clock.nowMillis
        try {
          publisher.publish(msg, session)
          if (logger.underlying.isDebugEnabled) {
            logMessage(s"Message sent user=${session.userId} AMQPMessageID=${msg.messageId}", msg)
          }
          tracker.track(
            id,
            clock.nowMillis,
            replyTimeout,
            attributes.checks,
            session,
            next,
            requestNameString,
            attributes.silent,
            onComplete = Some(() => trackerPool.decrementPendingAndEvict(qName)),
          )
        } catch {
          case e: Throwable =>
            trackerPool.decrementPendingAndEvict(qName)
            logger.error(e.getMessage, e)
            if (!attributes.silent)
              statsEngine.logResponse(
                session.scenario,
                session.groups,
                requestNameString,
                now,
                clock.nowMillis,
                KO,
                Some("500"),
                Some(e.getMessage),
              )
            next ! session.markAsFailed
        }
      case Failure(errorMessage) =>
        val now = clock.nowMillis
        logger.error(s"Failed to resolve reply destination: $errorMessage")
        if (!attributes.silent)
          statsEngine.logResponse(
            session.scenario,
            session.groups,
            requestNameString,
            now,
            clock.nowMillis,
            KO,
            Some("500"),
            Some(errorMessage),
          )
        next ! session.markAsFailed
    }
}
