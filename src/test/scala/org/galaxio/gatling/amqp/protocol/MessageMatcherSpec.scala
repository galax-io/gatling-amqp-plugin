package org.galaxio.gatling.amqp.protocol

import com.rabbitmq.client.AMQP
import org.galaxio.gatling.amqp.request.AmqpProtocolMessage
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class MessageMatcherSpec extends AnyFlatSpec with Matchers {

  private def mkMsg(
      messageId: String = null,
      correlationId: String = null,
  ): AmqpProtocolMessage =
    AmqpProtocolMessage(
      new AMQP.BasicProperties.Builder()
        .messageId(messageId)
        .correlationId(correlationId)
        .build(),
      Array.emptyByteArray,
    )

  // --- MessageIdMessageMatcher ---

  "MessageIdMessageMatcher" should "return messageId as requestMatchId" in {
    val msg = mkMsg(messageId = "msg-123")

    MessageIdMessageMatcher.requestMatchId(msg) shouldBe "msg-123"
  }

  it should "return messageId as responseMatchId" in {
    val msg = mkMsg(messageId = "msg-456")

    MessageIdMessageMatcher.responseMatchId(msg) shouldBe "msg-456"
  }

  it should "return message unchanged from prepareRequest" in {
    val msg    = mkMsg(messageId = "msg-789")
    val result = MessageIdMessageMatcher.prepareRequest(msg)

    result shouldBe theSameInstanceAs(msg)
  }

  // --- CorrelationIdMessageMatcher ---

  "CorrelationIdMessageMatcher" should "add a correlationId via prepareRequest" in {
    val msg    = mkMsg(messageId = "m1")
    val result = CorrelationIdMessageMatcher.prepareRequest(msg)

    result.correlationId should not be null
    result.correlationId should not be empty
  }

  it should "generate a UUID-format correlationId" in {
    val msg    = mkMsg()
    val result = CorrelationIdMessageMatcher.prepareRequest(msg)

    // UUID format: 8-4-4-4-12 hex chars
    result.correlationId should fullyMatch regex """[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"""
  }

  it should "return correlationId as requestMatchId" in {
    val msg = mkMsg(correlationId = "corr-abc")

    CorrelationIdMessageMatcher.requestMatchId(msg) shouldBe "corr-abc"
  }

  it should "return correlationId as responseMatchId" in {
    val msg = mkMsg(correlationId = "corr-def")

    CorrelationIdMessageMatcher.responseMatchId(msg) shouldBe "corr-def"
  }

  it should "not modify the original message in prepareRequest" in {
    val original = mkMsg(messageId = "m1", correlationId = null)
    val prepared = CorrelationIdMessageMatcher.prepareRequest(original)

    original.correlationId shouldBe null
    prepared.correlationId should not be null
  }

  // --- AmqpProtocolMessageMatcher ---

  "AmqpProtocolMessageMatcher" should "use custom extractor for requestMatchId" in {
    val extractor = (msg: AmqpProtocolMessage) => s"custom-${msg.messageId}"
    val matcher   = AmqpProtocolMessageMatcher(extractor)
    val msg       = mkMsg(messageId = "m1")

    matcher.requestMatchId(msg) shouldBe "custom-m1"
  }

  it should "use custom extractor for responseMatchId" in {
    val extractor = (msg: AmqpProtocolMessage) => s"resp-${msg.correlationId}"
    val matcher   = AmqpProtocolMessageMatcher(extractor)
    val msg       = mkMsg(correlationId = "c1")

    matcher.responseMatchId(msg) shouldBe "resp-c1"
  }

  it should "return message unchanged from prepareRequest (default trait behavior)" in {
    val extractor = (msg: AmqpProtocolMessage) => msg.messageId
    val matcher   = AmqpProtocolMessageMatcher(extractor)
    val msg       = mkMsg(messageId = "m1")
    val result    = matcher.prepareRequest(msg)

    result shouldBe theSameInstanceAs(msg)
  }

  it should "work with payload-based extractor" in {
    val extractor = (msg: AmqpProtocolMessage) => new String(msg.payload)
    val matcher   = AmqpProtocolMessageMatcher(extractor)
    val msg       = AmqpProtocolMessage(
      new AMQP.BasicProperties.Builder().build(),
      "my-routing-key".getBytes,
    )

    matcher.requestMatchId(msg) shouldBe "my-routing-key"
    matcher.responseMatchId(msg) shouldBe "my-routing-key"
  }
}
