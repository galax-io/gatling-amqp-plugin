package org.galaxio.gatling.amqp.action

import com.rabbitmq.client.GetResponse
import io.gatling.commons.stats.{KO, OK, Status}
import io.gatling.commons.util.Clock
import io.gatling.commons.validation.{Failure, Validation}
import io.gatling.core.action.{Action, RequestAction}
import io.gatling.core.actor.{ActorRef, Scheduler}
import io.gatling.core.check.Check
import io.gatling.core.controller.throttle.Throttler
import io.gatling.core.session.{Expression, Session}
import io.gatling.core.stats.StatsEngine
import io.gatling.core.util.NameGen
import org.galaxio.gatling.amqp.protocol.AmqpComponents
import org.galaxio.gatling.amqp.request.{AmqpProtocolMessage, ConsumeAttributes}

import scala.concurrent.duration._

class Consume(
    attributes: ConsumeAttributes,
    components: AmqpComponents,
    scheduler: Scheduler,
    val statsEngine: StatsEngine,
    val clock: Clock,
    val next: Action,
    throttler: Option[ActorRef[Throttler.Command]],
) extends RequestAction with AmqpLogging with NameGen {
  override val name: String = genName("amqpConsume")

  override val requestName: Expression[String] = attributes.requestName

  private val PollIntervalMillis = 100L

  override def sendRequest(session: Session): Validation[Unit] =
    for {
      reqName   <- requestName(session)
      queueName <- attributes.queueName(session)
    } yield throttler
      .fold(consumeAndLog(reqName, queueName, session))(
        _ ! Throttler.Command.ThrottledRequest(session.scenario, () => consumeAndLog(reqName, queueName, session)),
      )

  private def consumeAndLog(requestNameString: String, queueName: String, session: Session): Unit = {
    val requestStart = clock.nowMillis
    poll(requestNameString, queueName, session, requestStart, requestStart + attributes.timeout)
  }

  /** Non-blocking poll: each attempt borrows a channel, performs a single basicGet, and returns the channel immediately. If no
    * message is available and the deadline has not passed, the next attempt is scheduled on the Gatling scheduler thread rather
    * than blocking a user thread.
    */
  private def poll(
      requestNameString: String,
      queueName: String,
      session: Session,
      requestStart: Long,
      deadline: Long,
  ): Unit = {
    val pool = components.connectionPublishPool

    def logResult(s: Session, status: Status, responseCode: Option[String], message: Option[String]): Unit =
      if (!attributes.silent)
        statsEngine.logResponse(
          s.scenario,
          s.groups,
          requestNameString,
          requestStart,
          clock.nowMillis,
          status,
          responseCode,
          message,
        )

    val attempt: Either[Throwable, GetResponse] =
      try {
        val channel = pool.channel
        try Right(channel.basicGet(queueName, true))
        finally {
          if (channel.isOpen) pool.returnChannel(channel)
          else pool.invalidate(channel)
        }
      } catch {
        case e: Throwable => Left(e)
      }

    attempt match {
      case Left(e) =>
        logger.error(e.getMessage, e)
        logResult(session, KO, Some("500"), Some(e.getMessage))
        next ! session.markAsFailed

      case Right(response) if response != null =>
        val message             = AmqpProtocolMessage(response.getProps, response.getBody)
        val (newSession, error) = Check.check(message, session, attributes.checks)
        error match {
          case Some(Failure(errorMessage)) =>
            logResult(newSession, KO, message.responseCode, Some(errorMessage))
            next ! newSession.markAsFailed
          case _                           =>
            logResult(newSession, OK, message.responseCode, None)
            next ! newSession
        }

      case Right(_) =>
        val remaining = deadline - clock.nowMillis
        if (remaining <= 0) {
          logResult(session, KO, None, Some(s"No message in queue within ${attributes.timeout}ms"))
          next ! session.markAsFailed
        } else {
          val delay = math.min(PollIntervalMillis, remaining)
          scheduler.scheduleOnce(delay.millis)(poll(requestNameString, queueName, session, requestStart, deadline))
        }
    }
  }
}
