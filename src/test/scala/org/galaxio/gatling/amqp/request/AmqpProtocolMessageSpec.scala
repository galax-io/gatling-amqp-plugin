package org.galaxio.gatling.amqp.request

import com.rabbitmq.client.AMQP
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class AmqpProtocolMessageSpec extends AnyFlatSpec with Matchers {

  private def basicProps(
      messageId: String = null,
      correlationId: String = null,
  ): AMQP.BasicProperties =
    new AMQP.BasicProperties.Builder()
      .messageId(messageId)
      .correlationId(correlationId)
      .build()

  "AmqpProtocolMessage" should "return correlationId from properties" in {
    val msg = AmqpProtocolMessage(basicProps(correlationId = "corr-1"), Array.emptyByteArray)

    msg.correlationId shouldBe "corr-1"
  }

  it should "produce a copy with new correlationId" in {
    val original = AmqpProtocolMessage(basicProps(correlationId = "old-corr"), "hello".getBytes)
    val modified = original.correlationId("new-corr")

    modified.correlationId shouldBe "new-corr"
    original.correlationId shouldBe "old-corr"
  }

  it should "return messageId from properties" in {
    val msg = AmqpProtocolMessage(basicProps(messageId = "msg-1"), Array.emptyByteArray)

    msg.messageId shouldBe "msg-1"
  }

  it should "produce a copy with new messageId" in {
    val original = AmqpProtocolMessage(basicProps(messageId = "old-msg"), "data".getBytes)
    val modified = original.messageId("new-msg")

    modified.messageId shouldBe "new-msg"
    original.messageId shouldBe "old-msg"
  }

  it should "preserve payload after correlationId copy" in {
    val payload  = "test payload".getBytes
    val original = AmqpProtocolMessage(basicProps(), payload)
    val modified = original.correlationId("new-corr")

    modified.payload shouldBe payload
  }

  it should "preserve payload after messageId copy" in {
    val payload  = "test payload".getBytes
    val original = AmqpProtocolMessage(basicProps(), payload)
    val modified = original.messageId("new-msg")

    modified.payload shouldBe payload
  }

  it should "preserve responseCode after copy" in {
    val original = AmqpProtocolMessage(basicProps(), Array.emptyByteArray, responseCode = Some("200"))
    val modified = original.correlationId("new-corr")

    modified.responseCode shouldBe Some("200")
  }

  it should "default responseCode to None" in {
    val msg = AmqpProtocolMessage(basicProps(), Array.emptyByteArray)

    msg.responseCode shouldBe None
  }

  it should "not mutate the original message when setting correlationId" in {
    val original = AmqpProtocolMessage(basicProps(correlationId = "c1", messageId = "m1"), Array.emptyByteArray)
    val _        = original.correlationId("c2")

    original.correlationId shouldBe "c1"
    original.messageId shouldBe "m1"
  }

  it should "not mutate the original message when setting messageId" in {
    val original = AmqpProtocolMessage(basicProps(correlationId = "c1", messageId = "m1"), Array.emptyByteArray)
    val _        = original.messageId("m2")

    original.correlationId shouldBe "c1"
    original.messageId shouldBe "m1"
  }
}
