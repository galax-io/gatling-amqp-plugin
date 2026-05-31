package org.galaxio.gatling.amqp.integration

import com.dimafeng.testcontainers.{ForAllTestContainer, RabbitMQContainer}
import com.rabbitmq.client.ConnectionFactory
import io.gatling.amqp.testkit.CapturingStatsEngine
import io.gatling.commons.stats.{KO, OK}
import io.gatling.commons.util.DefaultClock
import io.gatling.core.action.Action
import io.gatling.core.actor.ActorSystem
import io.gatling.core.session.Session
import org.galaxio.gatling.amqp.AmqpCheck
import org.galaxio.gatling.amqp.Predef.simpleCheck
import org.galaxio.gatling.amqp.client.AmqpConnectionPool
import org.galaxio.gatling.amqp.protocol.AmqpComponents
import org.galaxio.gatling.amqp.request.ConsumeAttributes
import org.galaxio.gatling.amqp.action.Consume
import org.galaxio.gatling.amqp.tags.DockerTest
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.testcontainers.utility.DockerImageName

import java.util.concurrent.{CountDownLatch, TimeUnit}

class ConsumeTimeoutIntegrationSpec extends AnyWordSpec with Matchers with ForAllTestContainer with BeforeAndAfterAll {

  override val container: RabbitMQContainer = RabbitMQContainer(
    DockerImageName.parse("rabbitmq:3.13-management-alpine"),
  )

  private lazy val connectionFactory = {
    val cf = new ConnectionFactory()
    cf.setHost(container.host)
    cf.setPort(container.amqpPort)
    cf.setUsername(container.adminUsername)
    cf.setPassword(container.adminPassword)
    cf
  }

  private val clock           = new DefaultClock
  private lazy val system     = new ActorSystem
  private lazy val eventLoops = new io.netty.channel.nio.NioEventLoopGroup(1)

  override def afterAll(): Unit = {
    system.close()
    eventLoops.shutdownGracefully()
    super.afterAll()
  }

  private def terminalAction(onSession: Session => Unit): Action = new Action {
    override def name: String                    = "test-terminal"
    override def execute(session: Session): Unit = onSession(session)
  }

  private def newSession(): Session = Session("test-scenario", 0L, eventLoops.next())

  /** Drives a Consume action against the real broker. Returns a latch that fires when the action advances to `next`. */
  private def runConsume(
      pool: AmqpConnectionPool,
      queue: String,
      timeout: Long,
      stats: CapturingStatsEngine,
      checks: List[AmqpCheck] = Nil,
      silent: Boolean = false,
  ): CountDownLatch = {
    val advanced   = new CountDownLatch(1)
    val expr       = (_: Session) => io.gatling.commons.validation.Success(queue)
    val components = AmqpComponents(null, pool, null, null) // only connectionPublishPool is read by Consume
    val consume    = new Consume(
      ConsumeAttributes(expr, expr, timeout, checks, silent),
      components,
      system.scheduler,
      stats,
      clock,
      terminalAction(_ => advanced.countDown()),
      None,
    )
    consume.execute(newSession())
    advanced
  }

  "Consume action timeout" should {
    "fail with a timeout message when the queue stays empty" taggedAs DockerTest in {
      val pool = new AmqpConnectionPool(connectionFactory, 1, 4)
      try {
        declareQueue("consume_empty")
        val stats = new CapturingStatsEngine
        runConsume(pool, "consume_empty", 500L, stats)

        stats.latch.await(5, TimeUnit.SECONDS) shouldBe true
        stats.status.get() shouldBe KO
        stats.message.get().exists(_.contains("No message in queue within 500ms")) shouldBe true
      } finally pool.close()
    }

    "succeed when a message is already present" taggedAs DockerTest in {
      val pool = new AmqpConnectionPool(connectionFactory, 1, 4)
      try {
        declareQueue("consume_present")
        publish("consume_present", "ready")
        val stats = new CapturingStatsEngine
        runConsume(pool, "consume_present", 2000L, stats)

        stats.latch.await(5, TimeUnit.SECONDS) shouldBe true
        stats.status.get() shouldBe OK
      } finally pool.close()
    }

    "pick up a message that arrives mid-timeout without losing it" taggedAs DockerTest in {
      val pool      = new AmqpConnectionPool(connectionFactory, 1, 4)
      val publisher = new Thread(() => {
        Thread.sleep(300)
        publish("consume_delayed", "late")
      })
      try {
        declareQueue("consume_delayed")
        publisher.start()

        val stats = new CapturingStatsEngine
        runConsume(pool, "consume_delayed", 3000L, stats)

        stats.latch.await(5, TimeUnit.SECONDS) shouldBe true
        stats.status.get() shouldBe OK
      } finally {
        publisher.join()
        pool.close()
      }
    }

    "fail the check when the received message does not match" taggedAs DockerTest in {
      val pool = new AmqpConnectionPool(connectionFactory, 1, 4)
      try {
        declareQueue("consume_check")
        publish("consume_check", "actual")
        val stats = new CapturingStatsEngine
        runConsume(pool, "consume_check", 2000L, stats, checks = List(simpleCheck(_ => false)))

        stats.latch.await(5, TimeUnit.SECONDS) shouldBe true
        stats.status.get() shouldBe KO
      } finally pool.close()
    }

    "advance without logging a response when silent" taggedAs DockerTest in {
      val pool = new AmqpConnectionPool(connectionFactory, 1, 4)
      try {
        declareQueue("consume_silent")
        publish("consume_silent", "ready")
        val stats    = new CapturingStatsEngine
        val advanced = runConsume(pool, "consume_silent", 2000L, stats, silent = true)

        advanced.await(5, TimeUnit.SECONDS) shouldBe true            // action still advances to next
        stats.latch.await(500, TimeUnit.MILLISECONDS) shouldBe false // but no response was logged
      } finally pool.close()
    }
  }

  private def declareQueue(name: String): Unit           = withChannel(_.queueDeclare(name, false, false, true, null))
  private def publish(queue: String, body: String): Unit =
    withChannel(_.basicPublish("", queue, null, body.getBytes))

  private def withChannel(f: com.rabbitmq.client.Channel => Unit): Unit = {
    val conn    = connectionFactory.newConnection()
    val channel = conn.createChannel()
    try f(channel)
    finally {
      channel.close()
      conn.close()
    }
  }
}
