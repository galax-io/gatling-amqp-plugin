package org.galaxio.gatling.amqp.client

import io.gatling.commons.stats.{KO, OK, Status}
import io.gatling.commons.util.Clock
import io.gatling.commons.validation.Failure
import io.gatling.core.action.Action
import io.gatling.core.actor.{Actor, Behavior}
import io.gatling.core.check.Check
import io.gatling.core.session.Session
import io.gatling.core.stats.StatsEngine
import org.galaxio.gatling.amqp.AmqpCheck
import org.galaxio.gatling.amqp.client.AmqpMessageTrackerActor.{MessageConsumed, MessagePublished, TimeoutScan}
import org.galaxio.gatling.amqp.request.AmqpProtocolMessage

import scala.collection.mutable
import scala.concurrent.duration._

object AmqpMessageTrackerActor {

  sealed trait AmqpMessage

  final case class MessagePublished(
      matchId: String,
      sent: Long,
      replyTimeout: Long,
      checks: List[AmqpCheck],
      session: Session,
      next: Action,
      requestName: String,
      silent: Boolean = false,
  ) extends AmqpMessage

  final case class MessageConsumed(
      matchId: String,
      received: Long,
      message: AmqpProtocolMessage,
  ) extends AmqpMessage

  case object TimeoutScan extends AmqpMessage
}

class AmqpMessageTrackerActor(name: String, statsEngine: StatsEngine, clock: Clock)
    extends Actor[AmqpMessageTrackerActor.AmqpMessage](name) {

  private val sentMessages                 = mutable.HashMap.empty[String, MessagePublished]
  private val timedOutMessages             = mutable.ArrayBuffer.empty[MessagePublished]
  private var periodicTimeoutScanTriggered = false

  private def triggerPeriodicTimeoutScan(): Unit =
    if (!periodicTimeoutScanTriggered) {
      periodicTimeoutScanTriggered = true
      scheduler.scheduleAtFixedRate(1000.millis) {
        self ! TimeoutScan
      }
    }

  override def init(): Behavior[AmqpMessageTrackerActor.AmqpMessage] = {
    case messageSent: MessagePublished =>
      sentMessages += messageSent.matchId -> messageSent
      if (messageSent.replyTimeout > 0) {
        triggerPeriodicTimeoutScan()
      }
      stay

    case MessageConsumed(matchId, received, message) =>
      sentMessages.remove(matchId).foreach { case MessagePublished(_, sent, _, checks, session, next, requestName, silent) =>
        processMessage(session, sent, received, checks, message, next, requestName, silent)
      }
      stay

    case TimeoutScan =>
      val now = clock.nowMillis
      sentMessages.valuesIterator.foreach { messagePublished =>
        val replyTimeout = messagePublished.replyTimeout
        if (replyTimeout > 0 && (now - messagePublished.sent) > replyTimeout) {
          timedOutMessages += messagePublished
        }
      }

      for (MessagePublished(matchId, sent, receivedTimeout, _, session, next, requestName, silent) <- timedOutMessages) {
        sentMessages.remove(matchId)
        executeNext(
          session.markAsFailed,
          sent,
          now,
          KO,
          next,
          requestName,
          None,
          Some(s"Reply timeout after $receivedTimeout ms"),
          silent,
        )
      }
      timedOutMessages.clear()
      stay
  }

  private def executeNext(
      session: Session,
      sent: Long,
      received: Long,
      status: Status,
      next: Action,
      requestName: String,
      responseCode: Option[String],
      message: Option[String],
      silent: Boolean = false,
  ): Unit = {
    if (!silent)
      statsEngine.logResponse(
        session.scenario,
        session.groups,
        requestName,
        sent,
        received,
        status,
        responseCode,
        message,
      )
    next ! session.logGroupRequestTimings(sent, received)
  }

  private def processMessage(
      session: Session,
      sent: Long,
      received: Long,
      checks: List[AmqpCheck],
      message: AmqpProtocolMessage,
      next: Action,
      requestName: String,
      silent: Boolean,
  ): Unit = {
    val (newSession, error) = Check.check(message, session, checks)
    error match {
      case Some(Failure(errorMessage)) =>
        executeNext(
          newSession.markAsFailed,
          sent,
          received,
          KO,
          next,
          requestName,
          message.responseCode,
          Some(errorMessage),
          silent,
        )
      case _                           =>
        executeNext(newSession, sent, received, OK, next, requestName, message.responseCode, message.responseCode, silent)
    }
  }

}
