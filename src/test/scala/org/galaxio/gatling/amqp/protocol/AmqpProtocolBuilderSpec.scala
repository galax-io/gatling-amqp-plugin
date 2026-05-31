package org.galaxio.gatling.amqp.protocol

import com.rabbitmq.client.{BuiltinExchangeType, ConnectionFactory}
import org.galaxio.gatling.amqp.request.AmqpProtocolMessage
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class AmqpProtocolBuilderSpec extends AnyFlatSpec with Matchers {

  private val cf      = new ConnectionFactory()
  private val replyCf = new ConnectionFactory()

  private def defaultBuilder: AmqpProtocolBuilder = AmqpProtocolBuilder(cf, replyCf)

  "AmqpProtocolBuilder" should "have correct default values" in {
    val builder = defaultBuilder

    builder.deliveryMode shouldBe a[NonPersistent]
    builder.messageMatcher shouldBe MessageIdMessageMatcher
    builder.consumerThreadsCount shouldBe 1
    builder.replyTimeout shouldBe None
    builder.responseTransformer shouldBe None
    builder.initActions shouldBe Nil
    builder.replyInitActions shouldBe Nil
  }

  it should "set persistent delivery mode" in {
    val builder = defaultBuilder.usePersistentDeliveryMode

    builder.deliveryMode shouldBe a[Persistent]
    builder.deliveryMode.mode shouldBe 2
  }

  it should "set non-persistent delivery mode" in {
    val builder = defaultBuilder.usePersistentDeliveryMode.useNonPersistentDeliveryMode

    builder.deliveryMode shouldBe a[NonPersistent]
    builder.deliveryMode.mode shouldBe 1
  }

  it should "set MessageIdMessageMatcher via matchByMessageId" in {
    val builder = defaultBuilder.matchByCorrelationId.matchByMessageId

    builder.messageMatcher shouldBe MessageIdMessageMatcher
  }

  it should "set CorrelationIdMessageMatcher via matchByCorrelationId" in {
    val builder = defaultBuilder.matchByCorrelationId

    builder.messageMatcher shouldBe CorrelationIdMessageMatcher
  }

  it should "set custom AmqpProtocolMessageMatcher via matchByMessage" in {
    val extractor: AmqpProtocolMessage => String = msg => msg.correlationId
    val builder                                  = defaultBuilder.matchByMessage(extractor)

    builder.messageMatcher shouldBe a[AmqpProtocolMessageMatcher]
  }

  it should "set reply timeout" in {
    val builder = defaultBuilder.replyTimeout(5000L)

    builder.replyTimeout shouldBe Some(5000L)
  }

  it should "set consumer threads count" in {
    val builder = defaultBuilder.consumerThreadsCount(4)

    builder.consumerThreadsCount shouldBe 4
  }

  it should "set response transformer" in {
    val transformer: AmqpProtocolMessage => AmqpProtocolMessage = identity
    val builder                                                 = defaultBuilder.responseTransform(transformer)

    builder.responseTransformer shouldBe defined
  }

  it should "append QueueDeclare to initActions when declaring a queue" in {
    val q       = AmqpQueue("test-queue", durable = true, exclusive = false, autoDelete = false, arguments = Map.empty)
    val builder = defaultBuilder.declare(q)

    builder.initActions should have size 1
    builder.initActions.head shouldBe QueueDeclare(q)
  }

  it should "append ExchangeDeclare to initActions when declaring an exchange" in {
    val e       = AmqpExchange("test-exchange", BuiltinExchangeType.TOPIC, durable = true, autoDelete = false, arguments = Map.empty)
    val builder = defaultBuilder.declare(e)

    builder.initActions should have size 1
    builder.initActions.head shouldBe ExchangeDeclare(e)
  }

  it should "append BindQueue to initActions when binding a queue" in {
    val q       = AmqpQueue("test-queue", durable = true, exclusive = false, autoDelete = false, arguments = Map.empty)
    val e       = AmqpExchange("test-exchange", BuiltinExchangeType.TOPIC, durable = true, autoDelete = false, arguments = Map.empty)
    val builder = defaultBuilder.bindQueue(q, e, "routing.key")

    builder.initActions should have size 1
    builder.initActions.head shouldBe BindQueue("test-queue", "test-exchange", "routing.key", Map.empty)
  }

  it should "accumulate multiple initActions in order" in {
    val q = AmqpQueue("q1", durable = true, exclusive = false, autoDelete = false, arguments = Map.empty)
    val e = AmqpExchange("e1", BuiltinExchangeType.DIRECT, durable = true, autoDelete = false, arguments = Map.empty)

    val builder = defaultBuilder
      .declare(q)
      .declare(e)
      .bindQueue(q, e, "rk")

    builder.initActions should have size 3
    builder.initActions(0) shouldBe QueueDeclare(q)
    builder.initActions(1) shouldBe ExchangeDeclare(e)
    builder.initActions(2) shouldBe BindQueue("q1", "e1", "rk", Map.empty)
  }

  it should "build an AmqpProtocol with all settings preserved" in {
    val q = AmqpQueue("q1", durable = true, exclusive = false, autoDelete = false, arguments = Map.empty)

    val protocol = defaultBuilder.usePersistentDeliveryMode.matchByCorrelationId
      .replyTimeout(10000L)
      .consumerThreadsCount(2)
      .declare(q)
      .build

    protocol.connectionFactory shouldBe cf
    protocol.replyConnectionFactory shouldBe replyCf
    protocol.deliveryMode shouldBe a[Persistent]
    protocol.messageMatcher shouldBe CorrelationIdMessageMatcher
    protocol.replyTimeout shouldBe Some(10000L)
    protocol.consumersThreadCount shouldBe 2
    protocol.initActions should have size 1
  }

  it should "be created from AmqpProtocolBuilderBase with single ConnectionFactory" in {
    val builder = AmqpProtocolBuilderBase.connectionFactory(cf)

    builder.requestConnectionFactory shouldBe cf
    builder.replyConnectionFactory shouldBe cf
  }

  it should "be created from AmqpProtocolBuilderBase with separate ConnectionFactories" in {
    val builder = AmqpProtocolBuilderBase.connectionFactory(cf, replyCf)

    builder.requestConnectionFactory shouldBe cf
    builder.replyConnectionFactory shouldBe replyCf
  }

  it should "pass bindQueue args to BindQueue" in {
    val q    = AmqpQueue("q1", durable = true, exclusive = false, autoDelete = false, arguments = Map.empty)
    val e    = AmqpExchange("e1", BuiltinExchangeType.FANOUT, durable = true, autoDelete = false, arguments = Map.empty)
    val args = Map("x-match" -> ("all": Any))

    val builder = defaultBuilder.bindQueue(q, e, "rk", args)

    builder.initActions.head shouldBe BindQueue("q1", "e1", "rk", args)
  }

  it should "append QueueDeclare to replyInitActions when declaring a reply queue" in {
    val q       = AmqpQueue("reply-queue", durable = true, exclusive = false, autoDelete = false, arguments = Map.empty)
    val builder = defaultBuilder.replyDeclare(q)

    builder.replyInitActions should have size 1
    builder.replyInitActions.head shouldBe QueueDeclare(q)
    builder.initActions shouldBe Nil
  }

  it should "append ExchangeDeclare to replyInitActions when declaring a reply exchange" in {
    val e       =
      AmqpExchange("reply-exchange", BuiltinExchangeType.TOPIC, durable = true, autoDelete = false, arguments = Map.empty)
    val builder = defaultBuilder.replyDeclare(e)

    builder.replyInitActions should have size 1
    builder.replyInitActions.head shouldBe ExchangeDeclare(e)
    builder.initActions shouldBe Nil
  }

  it should "append BindQueue to replyInitActions when binding a reply queue" in {
    val q       = AmqpQueue("reply-queue", durable = true, exclusive = false, autoDelete = false, arguments = Map.empty)
    val e       = AmqpExchange("reply-exchange", BuiltinExchangeType.TOPIC, durable = true, autoDelete = false, arguments = Map.empty)
    val builder = defaultBuilder.replyBindQueue(q, e, "reply.key")

    builder.replyInitActions should have size 1
    builder.replyInitActions.head shouldBe BindQueue("reply-queue", "reply-exchange", "reply.key", Map.empty)
    builder.initActions shouldBe Nil
  }

  it should "keep request and reply init actions separate" in {
    val reqQ   = AmqpQueue("req-queue", durable = true, exclusive = false, autoDelete = false, arguments = Map.empty)
    val replyQ = AmqpQueue("reply-queue", durable = true, exclusive = false, autoDelete = false, arguments = Map.empty)
    val e      = AmqpExchange("e1", BuiltinExchangeType.DIRECT, durable = true, autoDelete = false, arguments = Map.empty)

    val builder = defaultBuilder
      .declare(reqQ)
      .declare(e)
      .replyDeclare(replyQ)
      .replyDeclare(e)
      .replyBindQueue(replyQ, e, "reply.rk")

    builder.initActions should have size 2
    builder.initActions(0) shouldBe QueueDeclare(reqQ)
    builder.initActions(1) shouldBe ExchangeDeclare(e)

    builder.replyInitActions should have size 3
    builder.replyInitActions(0) shouldBe QueueDeclare(replyQ)
    builder.replyInitActions(1) shouldBe ExchangeDeclare(e)
    builder.replyInitActions(2) shouldBe BindQueue("reply-queue", "e1", "reply.rk", Map.empty)
  }

  it should "build an AmqpProtocol with replyInitActions preserved" in {
    val q      = AmqpQueue("q1", durable = true, exclusive = false, autoDelete = false, arguments = Map.empty)
    val replyQ = AmqpQueue("reply-q1", durable = true, exclusive = false, autoDelete = false, arguments = Map.empty)

    val protocol = defaultBuilder
      .declare(q)
      .replyDeclare(replyQ)
      .build

    protocol.initActions should have size 1
    protocol.initActions.head shouldBe QueueDeclare(q)
    protocol.replyInitActions should have size 1
    protocol.replyInitActions.head shouldBe QueueDeclare(replyQ)
  }
}
