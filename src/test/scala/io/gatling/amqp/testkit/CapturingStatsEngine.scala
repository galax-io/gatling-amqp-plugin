package io.gatling.amqp.testkit

import io.gatling.commons.stats.Status
import io.gatling.core.actor.ActorRef
import io.gatling.core.controller.Controller
import io.gatling.core.session.GroupBlock
import io.gatling.core.stats.StatsEngine

import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReference

/** Test double that captures the single logResponse a request action emits and signals completion via a latch.
  *
  * Lives in an `io.gatling`-rooted package so the package-private `Controller.Command` type referenced by [[StatsEngine.stop]]
  * can be named when implementing the trait.
  */
final class CapturingStatsEngine extends StatsEngine {
  val latch: CountDownLatch                    = new CountDownLatch(1)
  val status: AtomicReference[Status]          = new AtomicReference[Status]()
  val message: AtomicReference[Option[String]] = new AtomicReference[Option[String]](None)

  override def logResponse(
      scenario: String,
      groups: List[String],
      requestName: String,
      startTimestamp: Long,
      endTimestamp: Long,
      st: Status,
      responseCode: Option[String],
      msg: Option[String],
  ): Unit = {
    status.set(st)
    message.set(msg)
    latch.countDown()
  }

  override def start(): Unit                                                                                     = ()
  override def stop(controller: ActorRef[Controller.Command], exception: Option[Exception]): Unit                = ()
  override def logUserStart(scenario: String): Unit                                                              = ()
  override def logUserEnd(scenario: String): Unit                                                                = ()
  override def logGroupEnd(scenario: String, group: GroupBlock, exitTimestamp: Long): Unit                       = ()
  override def logRequestCrash(scenario: String, groups: List[String], requestName: String, error: String): Unit = ()
}
