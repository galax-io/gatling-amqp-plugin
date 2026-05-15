package org.galaxio.gatling.amqp.request

import io.gatling.commons.validation._
import io.gatling.core.config.GatlingConfiguration
import io.gatling.core.session.Session
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ConsumeDslBuilderSpec extends AnyFlatSpec with Matchers {

  private val configuration = GatlingConfiguration.loadForTest()

  private val requestName = (_: Session) => "test-consume".success
  private val queueName   = (_: Session) => "test-queue".success

  private def defaultBuilder: ConsumeDslBuilder =
    ConsumeDslBuilder(ConsumeAttributes(requestName, queueName), configuration)

  "ConsumeDslBuilder" should "have default timeout of 5000" in {
    val builder = defaultBuilder

    builder.attributes.timeout shouldBe 5000L
  }

  it should "set timeout value" in {
    val builder = defaultBuilder.timeout(10000L)

    builder.attributes.timeout shouldBe 10000L
  }

  it should "have empty checks list by default" in {
    val builder = defaultBuilder

    builder.attributes.checks shouldBe empty
  }

  it should "have silent as false by default" in {
    val builder = defaultBuilder

    builder.attributes.silent shouldBe false
  }

  it should "set silent flag to true" in {
    val builder = defaultBuilder.silent

    builder.attributes.silent shouldBe true
  }

  it should "build an ActionBuilder" in {
    val actionBuilder = defaultBuilder.build()

    actionBuilder should not be null
  }

  it should "be immutable - timeout returns new instance" in {
    val original = defaultBuilder
    val modified = original.timeout(20000L)

    original.attributes.timeout shouldBe 5000L
    modified.attributes.timeout shouldBe 20000L
  }

  it should "be immutable - silent returns new instance" in {
    val original = defaultBuilder
    val modified = original.silent

    original.attributes.silent shouldBe false
    modified.attributes.silent shouldBe true
  }

  "ConsumeDslBuilderQueue" should "create ConsumeDslBuilder with queue name" in {
    val queueBuilder = ConsumeDslBuilderQueue(requestName, configuration)
    val builder      = queueBuilder.queue(queueName)

    builder.attributes.queueName should not be null
    builder.attributes.requestName should not be null
  }
}
