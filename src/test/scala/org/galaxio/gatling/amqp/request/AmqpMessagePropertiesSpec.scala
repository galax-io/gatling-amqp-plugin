package org.galaxio.gatling.amqp.request

import io.gatling.commons.stats.OK
import io.gatling.commons.validation._
import io.gatling.core.session.Session
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class AmqpMessagePropertiesSpec extends AnyFlatSpec with Matchers {

  private val session = Session(
    scenario = "test",
    userId = 0L,
    attributes = Map.empty,
    baseStatus = OK,
    blockStack = Nil,
    onExit = Session.NothingOnExit,
    eventLoop = null,
  )

  "AmqpMessageProperties.toBasicProperties" should "produce BasicProperties with all null fields when empty" in {
    val props  = AmqpMessageProperties()
    val result = AmqpMessageProperties.toBasicProperties(props, session)

    result shouldBe a[Success[_]]
    val bp = result.toOption.get
    bp.getContentType shouldBe null
    bp.getContentEncoding shouldBe null
    bp.getDeliveryMode shouldBe null
    bp.getPriority shouldBe null
    bp.getCorrelationId shouldBe null
    bp.getReplyTo shouldBe null
    bp.getExpiration shouldBe null
    bp.getMessageId shouldBe null
    bp.getTimestamp shouldBe null
    bp.getType shouldBe null
    bp.getUserId shouldBe null
    bp.getAppId shouldBe null
    bp.getClusterId shouldBe null
    // With empty headers map, the builder sets an empty Java map (not null)
    bp.getHeaders shouldBe empty
  }

  it should "set contentType correctly" in {
    val props  = AmqpMessageProperties(contentType = Some((_: Session) => "application/json".success))
    val result = AmqpMessageProperties.toBasicProperties(props, session)

    result shouldBe a[Success[_]]
    result.toOption.get.getContentType shouldBe "application/json"
  }

  it should "set contentEncoding correctly" in {
    val props  = AmqpMessageProperties(contentEncoding = Some((_: Session) => "utf-8".success))
    val result = AmqpMessageProperties.toBasicProperties(props, session)

    result shouldBe a[Success[_]]
    result.toOption.get.getContentEncoding shouldBe "utf-8"
  }

  it should "set deliveryMode correctly" in {
    val props  = AmqpMessageProperties(deliveryMode = Some((_: Session) => 2.success))
    val result = AmqpMessageProperties.toBasicProperties(props, session)

    result shouldBe a[Success[_]]
    result.toOption.get.getDeliveryMode shouldBe 2
  }

  it should "set priority correctly (not deliveryMode)" in {
    val props  = AmqpMessageProperties(priority = Some((_: Session) => 5.success))
    val result = AmqpMessageProperties.toBasicProperties(props, session)

    result shouldBe a[Success[_]]
    val bp = result.toOption.get
    bp.getPriority shouldBe 5
    bp.getDeliveryMode shouldBe null
  }

  it should "set both deliveryMode and priority independently" in {
    val props  = AmqpMessageProperties(
      deliveryMode = Some((_: Session) => 2.success),
      priority = Some((_: Session) => 5.success),
    )
    val result = AmqpMessageProperties.toBasicProperties(props, session)

    result shouldBe a[Success[_]]
    val bp = result.toOption.get
    bp.getDeliveryMode shouldBe 2
    bp.getPriority shouldBe 5
  }

  it should "set correlationId correctly" in {
    val props  = AmqpMessageProperties(correlationId = Some((_: Session) => "corr-123".success))
    val result = AmqpMessageProperties.toBasicProperties(props, session)

    result shouldBe a[Success[_]]
    result.toOption.get.getCorrelationId shouldBe "corr-123"
  }

  it should "set replyTo correctly" in {
    val props  = AmqpMessageProperties(replyTo = Some((_: Session) => "reply-queue".success))
    val result = AmqpMessageProperties.toBasicProperties(props, session)

    result shouldBe a[Success[_]]
    result.toOption.get.getReplyTo shouldBe "reply-queue"
  }

  it should "set expiration correctly" in {
    val props  = AmqpMessageProperties(expiration = Some((_: Session) => "60000".success))
    val result = AmqpMessageProperties.toBasicProperties(props, session)

    result shouldBe a[Success[_]]
    result.toOption.get.getExpiration shouldBe "60000"
  }

  it should "set messageId correctly" in {
    val props  = AmqpMessageProperties(messageId = Some((_: Session) => "msg-456".success))
    val result = AmqpMessageProperties.toBasicProperties(props, session)

    result shouldBe a[Success[_]]
    result.toOption.get.getMessageId shouldBe "msg-456"
  }

  it should "resolve and set headers" in {
    val headers: Map[String, io.gatling.core.session.Expression[AnyRef]] = Map(
      "x-custom-header" -> ((_: Session) => ("header-value": AnyRef).success),
      "x-another"       -> ((_: Session) => ("another-value": AnyRef).success),
    )
    val props                                                            = AmqpMessageProperties(headers = headers)
    val result                                                           = AmqpMessageProperties.toBasicProperties(props, session)

    result shouldBe a[Success[_]]
    val h = result.toOption.get.getHeaders
    h should not be null
    h.get("x-custom-header") shouldBe "header-value"
    h.get("x-another") shouldBe "another-value"
  }

  it should "set multiple properties together" in {
    val props  = AmqpMessageProperties(
      contentType = Some((_: Session) => "text/plain".success),
      contentEncoding = Some((_: Session) => "utf-8".success),
      correlationId = Some((_: Session) => "corr-789".success),
      messageId = Some((_: Session) => "msg-012".success),
      replyTo = Some((_: Session) => "reply-q".success),
      expiration = Some((_: Session) => "30000".success),
    )
    val result = AmqpMessageProperties.toBasicProperties(props, session)

    result shouldBe a[Success[_]]
    val bp = result.toOption.get
    bp.getContentType shouldBe "text/plain"
    bp.getContentEncoding shouldBe "utf-8"
    bp.getCorrelationId shouldBe "corr-789"
    bp.getMessageId shouldBe "msg-012"
    bp.getReplyTo shouldBe "reply-q"
    bp.getExpiration shouldBe "30000"
  }

  it should "set type property correctly" in {
    val props  = AmqpMessageProperties(`type` = Some((_: Session) => "my-type".success))
    val result = AmqpMessageProperties.toBasicProperties(props, session)

    result shouldBe a[Success[_]]
    result.toOption.get.getType shouldBe "my-type"
  }

  it should "set userId correctly" in {
    val props  = AmqpMessageProperties(userId = Some((_: Session) => "user1".success))
    val result = AmqpMessageProperties.toBasicProperties(props, session)

    result shouldBe a[Success[_]]
    result.toOption.get.getUserId shouldBe "user1"
  }

  it should "set appId correctly" in {
    val props  = AmqpMessageProperties(appId = Some((_: Session) => "my-app".success))
    val result = AmqpMessageProperties.toBasicProperties(props, session)

    result shouldBe a[Success[_]]
    result.toOption.get.getAppId shouldBe "my-app"
  }

  it should "set clusterId correctly" in {
    val props  = AmqpMessageProperties(clusterId = Some((_: Session) => "cluster-1".success))
    val result = AmqpMessageProperties.toBasicProperties(props, session)

    result shouldBe a[Success[_]]
    result.toOption.get.getClusterId shouldBe "cluster-1"
  }
}
