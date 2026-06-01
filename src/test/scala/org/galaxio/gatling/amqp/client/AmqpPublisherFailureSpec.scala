package org.galaxio.gatling.amqp.client

import com.rabbitmq.client.{ConnectionFactory, MessageProperties}
import io.gatling.commons.validation.Failure
import io.gatling.core.session.{Expression, Session}
import org.galaxio.gatling.amqp.protocol.{AmqpComponents, AmqpProtocol, CorrelationIdMessageMatcher, NonPersistent}
import org.galaxio.gatling.amqp.request._
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import org.scalatest.wordspec.AnyWordSpec

class AmqpPublisherFailureSpec extends AnyWordSpec with Matchers with TableDrivenPropertyChecks {

  private def protocol: AmqpProtocol = AmqpProtocol(
    connectionFactory = new ConnectionFactory(),
    replyConnectionFactory = new ConnectionFactory(),
    deliveryMode = NonPersistent(),
    replyTimeout = None,
    consumersThreadCount = 1,
    messageMatcher = CorrelationIdMessageMatcher,
    responseTransformer = None,
    initActions = Nil,
  )

  private val message                      = AmqpProtocolMessage(MessageProperties.MINIMAL_BASIC, Array.emptyByteArray)
  private val session                      = Session("scenario", 0L, null)
  private val failName: Expression[String] = _ => Failure("bad name")
  private val failKey: Expression[String]  = _ => Failure("bad key")
  private val okName: Expression[String]   = _ => io.gatling.commons.validation.Success("ok")

  private def publisher(destination: AmqpExchange): AmqpPublisher =
    new AmqpPublisher(destination, AmqpComponents(protocol, null, null, null))

  "AmqpPublisher.publish" should {
    "throw IllegalStateException carrying the underlying error when destination resolution fails" in {
      val destinations = Table(
        ("description", "destination", "expectedFragment"),
        ("queue name fails", AmqpQueueExchange(failName): AmqpExchange, "bad name"),
        ("direct exchange name fails", AmqpDirectExchange(failName, okName): AmqpExchange, "bad name"),
        ("direct exchange routing key fails", AmqpDirectExchange(okName, failKey): AmqpExchange, "bad key"),
        ("topic exchange name fails", AmqpTopicExchange(failName, okName): AmqpExchange, "bad name"),
        ("topic exchange routing key fails", AmqpTopicExchange(okName, failKey): AmqpExchange, "bad key"),
      )

      forAll(destinations) { (_, destination, expectedFragment) =>
        val thrown = intercept[IllegalStateException](publisher(destination).publish(message, session))
        thrown.getMessage should include("Failed to resolve AMQP destination")
        thrown.getMessage should include(expectedFragment)
      }
    }
  }
}
