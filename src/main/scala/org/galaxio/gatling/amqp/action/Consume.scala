package org.galaxio.gatling.amqp.action

import io.gatling.commons.stats.{KO, OK}
import io.gatling.commons.util.Clock
import io.gatling.commons.validation.{Failure, Validation}
import io.gatling.core.action.{Action, RequestAction}
import io.gatling.core.actor.ActorRef
import io.gatling.core.check.Check
import io.gatling.core.controller.throttle.Throttler
import io.gatling.core.session.{Expression, Session}
import io.gatling.core.stats.StatsEngine
import io.gatling.core.util.NameGen
import org.galaxio.gatling.amqp.protocol.AmqpComponents
import org.galaxio.gatling.amqp.request.{AmqpProtocolMessage, ConsumeAttributes}

class Consume(
    attributes: ConsumeAttributes,
    components: AmqpComponents,
    val statsEngine: StatsEngine,
    val clock: Clock,
    val next: Action,
    throttler: Option[ActorRef[Throttler.Command]],
) extends RequestAction with AmqpLogging with NameGen {
  override val name: String = genName("amqpConsume")

  override val requestName: Expression[String] = attributes.requestName

  override def sendRequest(session: Session): Validation[Unit] =
    for {
      reqName   <- requestName(session)
      queueName <- attributes.queueName(session)
    } yield throttler
      .fold(consumeAndLog(reqName, queueName, session))(
        _ ! Throttler.Command.ThrottledRequest(session.scenario, () => consumeAndLog(reqName, queueName, session)),
      )

  private def consumeAndLog(requestNameString: String, queueName: String, session: Session): Unit = {
    val now = clock.nowMillis
    try {
      val pool     = components.connectionPublishPool
      val channel  = pool.channel
      val response =
        try channel.basicGet(queueName, true)
        finally pool.returnChannel(channel)

      if (response == null) {
        if (!attributes.silent)
          statsEngine.logResponse(
            session.scenario,
            session.groups,
            requestNameString,
            now,
            clock.nowMillis,
            KO,
            None,
            Some("No message available in queue"),
          )
        next ! session.markAsFailed
      } else {
        val message             = AmqpProtocolMessage(response.getProps, response.getBody)
        val (newSession, error) = Check.check(message, session, attributes.checks)
        error match {
          case Some(Failure(errorMessage)) =>
            if (!attributes.silent)
              statsEngine.logResponse(
                newSession.scenario,
                newSession.groups,
                requestNameString,
                now,
                clock.nowMillis,
                KO,
                message.responseCode,
                Some(errorMessage),
              )
            next ! newSession.markAsFailed
          case _                           =>
            if (!attributes.silent)
              statsEngine.logResponse(
                newSession.scenario,
                newSession.groups,
                requestNameString,
                now,
                clock.nowMillis,
                OK,
                message.responseCode,
                None,
              )
            next ! newSession
        }
      }
    } catch {
      case e: Throwable =>
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
  }
}
