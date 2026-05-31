package org.galaxio.gatling.amqp.request

import io.gatling.commons.stats.OK
import io.gatling.commons.validation._
import io.gatling.core.action.Action
import io.gatling.core.action.builder.ActionBuilder
import io.gatling.core.session.{Expression, Session}
import io.gatling.core.structure.ScenarioContext
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class PublishDslBuilderSpec extends AnyFlatSpec with Matchers {

  private val session = Session(
    scenario = "test",
    userId = 0L,
    attributes = Map.empty,
    baseStatus = OK,
    blockStack = Nil,
    onExit = Session.NothingOnExit,
    eventLoop = null,
  )

  private val noopActionBuilder: ActionBuilder = new ActionBuilder {
    override def build(ctx: ScenarioContext, next: Action): Action = next
  }

  private val requestName: Expression[String] = (_: Session) => "test-request".success

  private val dummyExchange: AmqpExchange = AmqpQueueExchange((_: Session) => "test-queue".success)

  private val dummyMessage: AmqpMessage =
    TextAmqpMessage((_: Session) => "hello".success, java.nio.charset.StandardCharsets.UTF_8)

  private var capturedAttributes: Option[AmqpAttributes] = None

  private val capturingFactory: AmqpAttributes => ActionBuilder = attrs => {
    capturedAttributes = Some(attrs)
    noopActionBuilder
  }

  private def defaultAttrs: AmqpAttributes = AmqpAttributes(
    requestName = requestName,
    destination = dummyExchange,
    message = dummyMessage,
  )

  private def defaultBuilder: PublishDslBuilder = PublishDslBuilder(defaultAttrs, capturingFactory)

  override def withFixture(test: NoArgTest) = {
    capturedAttributes = None
    super.withFixture(test)
  }

  "PublishDslBuilder" should "set messageId in message properties" in {
    val expr    = (_: Session) => "msg-id-1".success
    val builder = defaultBuilder.messageId(expr)

    builder.attributes.messageProperties.messageId shouldBe defined
    builder.attributes.messageProperties.messageId.get(session) shouldBe "msg-id-1".success
  }

  it should "set priority in message properties" in {
    val expr    = (_: Session) => 5.success
    val builder = defaultBuilder.priority(expr)

    builder.attributes.messageProperties.priority shouldBe defined
    builder.attributes.messageProperties.priority.get(session) shouldBe 5.success
  }

  it should "set contentType in message properties" in {
    val expr    = (_: Session) => "application/json".success
    val builder = defaultBuilder.contentType(expr)

    builder.attributes.messageProperties.contentType shouldBe defined
    builder.attributes.messageProperties.contentType.get(session) shouldBe "application/json".success
  }

  it should "set contentEncoding in message properties" in {
    val expr    = (_: Session) => "utf-8".success
    val builder = defaultBuilder.contentEncoding(expr)

    builder.attributes.messageProperties.contentEncoding shouldBe defined
    builder.attributes.messageProperties.contentEncoding.get(session) shouldBe "utf-8".success
  }

  it should "set correlationId in message properties" in {
    val expr    = (_: Session) => "corr-1".success
    val builder = defaultBuilder.correlationId(expr)

    builder.attributes.messageProperties.correlationId shouldBe defined
    builder.attributes.messageProperties.correlationId.get(session) shouldBe "corr-1".success
  }

  it should "set replyTo in message properties" in {
    val expr    = (_: Session) => "reply-q".success
    val builder = defaultBuilder.replyTo(expr)

    builder.attributes.messageProperties.replyTo shouldBe defined
    builder.attributes.messageProperties.replyTo.get(session) shouldBe "reply-q".success
  }

  it should "set expiration in message properties" in {
    val expr    = (_: Session) => "60000".success
    val builder = defaultBuilder.expiration(expr)

    builder.attributes.messageProperties.expiration shouldBe defined
    builder.attributes.messageProperties.expiration.get(session) shouldBe "60000".success
  }

  it should "add a single header" in {
    val builder = defaultBuilder.header("x-custom", (_: Session) => "value1".success)

    builder.attributes.messageProperties.headers should have size 1
    builder.attributes.messageProperties.headers("x-custom")(session) shouldBe "value1".success
  }

  it should "add multiple headers via headers method" in {
    val builder = defaultBuilder.headers(
      "h1" -> ((_: Session) => "v1".success),
      "h2" -> ((_: Session) => "v2".success),
    )

    builder.attributes.messageProperties.headers should have size 2
  }

  it should "accumulate headers from multiple header calls" in {
    val builder = defaultBuilder
      .header("h1", (_: Session) => "v1".success)
      .header("h2", (_: Session) => "v2".success)

    builder.attributes.messageProperties.headers should have size 2
  }

  it should "call factory with final attributes when build is invoked" in {
    val expr    = (_: Session) => "msg-42".success
    val builder = defaultBuilder.messageId(expr)

    builder.build()

    capturedAttributes shouldBe defined
    capturedAttributes.get.messageProperties.messageId shouldBe defined
  }

  it should "set amqpType in message properties" in {
    val expr    = (_: Session) => "my-type".success
    val builder = defaultBuilder.amqpType(expr)

    builder.attributes.messageProperties.`type` shouldBe defined
    builder.attributes.messageProperties.`type`.get(session) shouldBe "my-type".success
  }

  it should "set userId in message properties" in {
    val expr    = (_: Session) => "user1".success
    val builder = defaultBuilder.userId(expr)

    builder.attributes.messageProperties.userId shouldBe defined
    builder.attributes.messageProperties.userId.get(session) shouldBe "user1".success
  }

  it should "set appId in message properties" in {
    val expr    = (_: Session) => "app1".success
    val builder = defaultBuilder.appId(expr)

    builder.attributes.messageProperties.appId shouldBe defined
    builder.attributes.messageProperties.appId.get(session) shouldBe "app1".success
  }

  it should "set clusterId in message properties" in {
    val expr    = (_: Session) => "cluster1".success
    val builder = defaultBuilder.clusterId(expr)

    builder.attributes.messageProperties.clusterId shouldBe defined
    builder.attributes.messageProperties.clusterId.get(session) shouldBe "cluster1".success
  }

  it should "not modify original builder when setting properties" in {
    val original = defaultBuilder
    val modified = original.messageId((_: Session) => "msg-1".success)

    original.attributes.messageProperties.messageId shouldBe None
    modified.attributes.messageProperties.messageId shouldBe defined
  }
}
