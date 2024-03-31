package org.galaxio.gatling.amqp.client

import akka.actor.ActorRef
import io.gatling.core.action.Action
import io.gatling.core.session.Session
import org.galaxio.gatling.amqp.AmqpCheck
import org.galaxio.gatling.amqp.client.AmqpMessageTrackerActor.MessagePublished

class AmqpMessageTracker(actor: ActorRef) {

  def track(
      matchId: String,
      sent: Long,
      replyTimeout: Long,
      checks: List[AmqpCheck],
      session: Session,
      next: Action,
      requestName: String,
  ): Unit =
    actor ! MessagePublished(
      matchId,
      sent,
      replyTimeout,
      checks,
      session,
      next,
      requestName,
    )
}
