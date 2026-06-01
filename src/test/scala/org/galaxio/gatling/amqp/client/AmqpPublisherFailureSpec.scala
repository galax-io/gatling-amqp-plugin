package org.galaxio.gatling.amqp.client

import com.rabbitmq.client.{ConnectionFactory, MessageProperties}
import io.gatling.commons.validation.{Failure, Success, Validation}
import io.gatling.core.session.Session
import org.galaxio.gatling.amqp.protocol._
import org.galaxio.gatling.amqp.request.{AmqpProtocolMessage, AmqpQueueExchange}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class AmqpPublisherFailureSpec extends AnyWordSpec with Matchers {

  private def buildProtocol(): AmqpProtocol = AmqpProtocol(
    connectionFactory = new ConnectionFactory(),
    replyConnectionFactory = new ConnectionFactory(),
    deliveryMode = NonPersistent(),
    replyTimeout = None,
    consumersThreadCount = 1,
    messageMatcher = CorrelationIdMessageMatcher,
    responseTransformer = None,
    initActions = Nil,
  )

  private val message = AmqpProtocolMessage(MessageProperties.MINIMAL_BASIC, Array.emptyByteArray)
  private val session = Session("scenario", 0L, null)

  "AmqpPublisher.publish" should {
    "throw when destination name resolution fails" in {
      val failingName: io.gatling.core.session.Expression[String] = _ => Failure("bad destination")
      val destination                                             = AmqpQueueExchange(failingName)
      val components                                              = AmqpComponents(buildProtocol(), null, null, null)
      val publisher                                               = new AmqpPublisher(destination, components)

      val thrown = intercept[RuntimeException](publisher.publish(message, session))
      thrown.getMessage should include("bad destination")
    }

    "succeed-validate when destination resolves but channel work is exercised separately" in {
      val okName: io.gatling.core.session.Expression[String] = _ => Success("queue-x")
      val destination                                        = AmqpQueueExchange(okName)
      val components                                         = AmqpComponents(buildProtocol(), null, null, null)
      val publisher                                          = new AmqpPublisher(destination, components)

      // Pool is null — channel access will NPE, proving destination resolved (got past dispatch).
      val ex: Throwable = intercept[Throwable](publisher.publish(message, session))
      ex shouldBe a[NullPointerException]
    }

    "validate destination resolution as Validation" in {
      val name: io.gatling.core.session.Expression[String] = _ => Failure("nope")
      val v: Validation[String]                            = name(session)
      v shouldBe a[Failure]
    }
  }
}
