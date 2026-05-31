package org.galaxio.gatling.amqp.integration

import com.dimafeng.testcontainers.{ForAllTestContainer, RabbitMQContainer}
import com.rabbitmq.client._
import io.gatling.commons.stats.Status
import io.gatling.commons.util.Clock
import io.gatling.core.action.Action
import io.gatling.core.actor.ActorSystem
import io.gatling.core.session.Session
import io.gatling.core.stats.NoOpStatsEngine
import org.galaxio.gatling.amqp.client.{AmqpConnectionPool, TrackerPool}
import org.galaxio.gatling.amqp.protocol.CorrelationIdMessageMatcher
import org.galaxio.gatling.amqp.request.AmqpProtocolMessage
import org.galaxio.gatling.amqp.tags.DockerTest
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.testcontainers.utility.DockerImageName

import java.util.UUID
import java.util.concurrent.{ConcurrentLinkedQueue, CountDownLatch, TimeUnit}
import scala.annotation.tailrec

class AmqpPluginIntegrationSpec extends AnyWordSpec with Matchers with ForAllTestContainer with BeforeAndAfterAll {

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

  private var actorSystem: ActorSystem = _

  override def beforeAll(): Unit = {
    super.beforeAll()
    actorSystem = new ActorSystem()
  }

  override def afterAll(): Unit = {
    if (actorSystem != null) actorSystem.close()
    super.afterAll()
  }

  private val testClock: Clock = new Clock {
    override def nowMillis: Long = System.currentTimeMillis()
  }

  private def recordingStatsEngine(): (NoOpStatsEngine, ConcurrentLinkedQueue[(String, Status, Option[String])]) = {
    val log    = new ConcurrentLinkedQueue[(String, Status, Option[String])]()
    val engine = new NoOpStatsEngine {
      override def logResponse(
          scenario: String,
          groups: List[String],
          requestName: String,
          startTimestamp: Long,
          endTimestamp: Long,
          status: Status,
          responseCode: Option[String],
          message: Option[String],
      ): Unit = log.add((requestName, status, message))
    }
    (engine, log)
  }

  // Bypasses Action.! which dispatches via session.eventLoop (null in tests)
  private def capturingAction(
      actionName: String,
      latch: CountDownLatch,
      sessions: ConcurrentLinkedQueue[Session],
  ): Action =
    new Action {
      override val name: String                    = actionName
      override def execute(session: Session): Unit = {
        sessions.add(session)
        latch.countDown()
      }
      override def !(session: Session): Unit       = execute(session)
    }

  @tailrec
  private def awaitConsumerCount(queue: String, expected: Int, remainingMs: Int = 5000): Unit =
    if (remainingMs <= 0) fail(s"Timed out waiting for $expected consumers on $queue")
    else {
      val conn    = connectionFactory.newConnection()
      val channel = conn.createChannel()
      val count   =
        try channel.consumerCount(queue)
        finally {
          channel.close()
          conn.close()
        }
      if (count != expected) {
        Thread.sleep(50)
        awaitConsumerCount(queue, expected, remainingMs - 50)
      }
    }

  private def deleteQueue(queue: String): Unit = {
    val conn    = connectionFactory.newConnection()
    val channel = conn.createChannel()
    try channel.queueDelete(queue)
    finally {
      channel.close()
      conn.close()
    }
  }

  "AmqpConnectionPool integration" should {

    "handle concurrent channel borrows without contention" taggedAs DockerTest in {
      val pool = new AmqpConnectionPool(connectionFactory, 2, 8)
      try {
        val latch  = new CountDownLatch(8)
        val errors = new ConcurrentLinkedQueue[Throwable]()

        (1 to 8).foreach { _ =>
          new Thread(() =>
            try {
              val ch = pool.channel
              ch.isOpen shouldBe true
              Thread.sleep(50)
              pool.returnChannel(ch)
              latch.countDown()
            } catch {
              case e: Throwable =>
                errors.add(e)
                latch.countDown()
            },
          ).start()
        }

        latch.await(30, TimeUnit.SECONDS) shouldBe true
        errors.size() shouldBe 0
      } finally {
        pool.close()
      }
    }

    "invalidate broken channels and create fresh ones" taggedAs DockerTest in {
      val pool = new AmqpConnectionPool(connectionFactory, 2, 4)
      try {
        val ch1 = pool.channel
        ch1.isOpen shouldBe true
        ch1.close()
        pool.invalidate(ch1)

        val ch2 = pool.channel
        ch2.isOpen shouldBe true
        pool.returnChannel(ch2)
      } finally {
        pool.close()
      }
    }

    "create independent consumer channels" taggedAs DockerTest in {
      val pool = new AmqpConnectionPool(connectionFactory, 2, 4)
      try {
        val consumer1 = pool.createConsumerChannel
        val consumer2 = pool.createConsumerChannel
        consumer1.isOpen shouldBe true
        consumer2.isOpen shouldBe true
        consumer1.getChannelNumber should not be consumer2.getChannelNumber
        consumer1.close()
        consumer2.close()
      } finally {
        pool.close()
      }
    }
  }

  "TrackerPool request-reply" should {

    "correlate request and reply via correlationId" taggedAs DockerTest in {
      val (statsEngine, _) = recordingStatsEngine()
      val replyPool        = new AmqpConnectionPool(connectionFactory, 2, 4)
      val publishPool      = new AmqpConnectionPool(connectionFactory, 2, 4)
      val replyQueue       = "test_rr_reply_" + UUID.randomUUID().toString.take(8)

      try {
        val trackerPool = new TrackerPool(replyPool, actorSystem, statsEngine, testClock)

        val pubChannel = publishPool.channel
        pubChannel.queueDeclare(replyQueue, false, false, false, null)

        val tracker = trackerPool.tracker(replyQueue, 1, CorrelationIdMessageMatcher, None)

        val latch    = new CountDownLatch(1)
        val sessions = new ConcurrentLinkedQueue[Session]()
        val action   = capturingAction("capture", latch, sessions)
        val session  = Session("test", 1L, null)

        val corrId  = UUID.randomUUID().toString
        val props   = new AMQP.BasicProperties.Builder().correlationId(corrId).build()
        val message = AmqpProtocolMessage(props, "request-body".getBytes)
        val matchId = CorrelationIdMessageMatcher.requestMatchId(message)

        tracker.track(matchId, testClock.nowMillis, 10000L, Nil, session, action, "test-rr", false)

        awaitConsumerCount(replyQueue, 1)

        val replyProps = new AMQP.BasicProperties.Builder().correlationId(corrId).build()
        pubChannel.basicPublish("", replyQueue, replyProps, "reply-body".getBytes)

        latch.await(10, TimeUnit.SECONDS) shouldBe true
        sessions.size() shouldBe 1
        sessions.peek().isFailed shouldBe false

        publishPool.returnChannel(pubChannel)
        trackerPool.close()
      } finally {
        publishPool.close()
        replyPool.close()
        deleteQueue(replyQueue)
      }
    }

    "timeout when no reply arrives" taggedAs DockerTest in {
      val (statsEngine, statsLog) = recordingStatsEngine()
      val replyPool               = new AmqpConnectionPool(connectionFactory, 2, 4)
      val replyQueue              = "test_timeout_reply_" + UUID.randomUUID().toString.take(8)

      try {
        val trackerPool = new TrackerPool(replyPool, actorSystem, statsEngine, testClock)

        val setupChannel = replyPool.createConsumerChannel
        setupChannel.queueDeclare(replyQueue, false, false, false, null)
        setupChannel.close()

        val tracker = trackerPool.tracker(replyQueue, 1, CorrelationIdMessageMatcher, None)

        val latch    = new CountDownLatch(1)
        val sessions = new ConcurrentLinkedQueue[Session]()
        val action   = capturingAction("capture", latch, sessions)
        val session  = Session("test", 1L, null)

        val matchId = UUID.randomUUID().toString

        tracker.track(matchId, testClock.nowMillis, 2000L, Nil, session, action, "test-timeout", false)

        latch.await(10, TimeUnit.SECONDS) shouldBe true
        sessions.size() shouldBe 1
        sessions.peek().isFailed shouldBe true

        import scala.jdk.CollectionConverters._
        val logged = statsLog.asScala.toList
        logged should have size 1
        logged.head._2 shouldBe io.gatling.commons.stats.KO
        logged.head._3.get should include("Reply timeout")

        trackerPool.close()
      } finally {
        replyPool.close()
        deleteQueue(replyQueue)
      }
    }

    "handle concurrent request-reply flows" taggedAs DockerTest in {
      val (statsEngine, _) = recordingStatsEngine()
      val replyPool        = new AmqpConnectionPool(connectionFactory, 4, 8)
      val publishPool      = new AmqpConnectionPool(connectionFactory, 2, 8)
      val replyQueue       = "test_concurrent_reply_" + UUID.randomUUID().toString.take(8)

      try {
        val trackerPool = new TrackerPool(replyPool, actorSystem, statsEngine, testClock)

        val setupChannel = publishPool.channel
        setupChannel.queueDeclare(replyQueue, false, false, false, null)
        publishPool.returnChannel(setupChannel)

        val tracker = trackerPool.tracker(replyQueue, 2, CorrelationIdMessageMatcher, None)

        val concurrency             = 10
        val completionLatch         = new CountDownLatch(concurrency)
        val allSessionsReceived     = new ConcurrentLinkedQueue[Session]()
        val publishedCorrelationIds = new ConcurrentLinkedQueue[String]()

        (1 to concurrency).foreach { i =>
          val corrId  = UUID.randomUUID().toString
          val props   = new AMQP.BasicProperties.Builder().correlationId(corrId).build()
          val message = AmqpProtocolMessage(props, s"request-$i".getBytes)
          val matchId = CorrelationIdMessageMatcher.requestMatchId(message)

          val action  = capturingAction(s"capture-$i", completionLatch, allSessionsReceived)
          val session = Session("test", i.toLong, null)
          publishedCorrelationIds.add(corrId)

          tracker.track(matchId, testClock.nowMillis, 10000L, Nil, session, action, s"rr-$i", false)
        }

        awaitConsumerCount(replyQueue, 2)

        import scala.jdk.CollectionConverters._
        publishedCorrelationIds.asScala.foreach { corrId =>
          val replyProps = new AMQP.BasicProperties.Builder().correlationId(corrId).build()
          val pubChannel = publishPool.channel
          pubChannel.basicPublish("", replyQueue, replyProps, s"reply-$corrId".getBytes)
          publishPool.returnChannel(pubChannel)
        }

        completionLatch.await(30, TimeUnit.SECONDS) shouldBe true
        allSessionsReceived.size() shouldBe concurrency
        allSessionsReceived.asScala.count(_.isFailed) shouldBe 0

        trackerPool.close()
      } finally {
        publishPool.close()
        replyPool.close()
        deleteQueue(replyQueue)
      }
    }
  }

  "TrackerPool resource cleanup" should {

    "close cancels consumers and closes channels" taggedAs DockerTest in {
      val (statsEngine, _) = recordingStatsEngine()
      val replyPool        = new AmqpConnectionPool(connectionFactory, 2, 4)
      val replyQueue       = "test_cleanup_" + UUID.randomUUID().toString.take(8)

      try {
        val trackerPool = new TrackerPool(replyPool, actorSystem, statsEngine, testClock)

        val conn    = connectionFactory.newConnection()
        val channel = conn.createChannel()
        channel.queueDeclare(replyQueue, false, false, false, null)
        channel.close()
        conn.close()

        trackerPool.tracker(replyQueue, 3, CorrelationIdMessageMatcher, None)

        awaitConsumerCount(replyQueue, 3)

        trackerPool.close()

        awaitConsumerCount(replyQueue, 0)
      } finally {
        replyPool.close()
        deleteQueue(replyQueue)
      }
    }

    "pool close releases all channels" taggedAs DockerTest in {
      val pool = new AmqpConnectionPool(connectionFactory, 2, 4)
      val ch1  = pool.channel
      val ch2  = pool.channel
      pool.returnChannel(ch1)
      pool.returnChannel(ch2)

      pool.close()

      ch1.isOpen shouldBe false
      ch2.isOpen shouldBe false
    }
  }

  "Publish and consume through connection pool" should {

    "publish via pool channel and consume via consumer channel" taggedAs DockerTest in {
      val pool = new AmqpConnectionPool(connectionFactory, 2, 4)
      try {
        val queue = "test_pool_pubsub_" + UUID.randomUUID().toString.take(8)

        val pubChannel = pool.channel
        pubChannel.queueDeclare(queue, false, false, true, null)
        pubChannel.basicPublish("", queue, null, "pool-message".getBytes)
        pool.returnChannel(pubChannel)

        val consumerChannel = pool.createConsumerChannel
        val latch           = new CountDownLatch(1)
        var received        = ""

        consumerChannel.basicConsume(
          queue,
          true,
          (_: String, delivery: Delivery) => {
            received = new String(delivery.getBody)
            latch.countDown()
          },
          (_: String) => (),
        )

        latch.await(10, TimeUnit.SECONDS) shouldBe true
        received shouldBe "pool-message"
        consumerChannel.close()
      } finally {
        pool.close()
      }
    }

    "handle multiple sequential publish-consume cycles" taggedAs DockerTest in {
      val pool = new AmqpConnectionPool(connectionFactory, 2, 4)
      try {
        val queue = "test_sequential_" + UUID.randomUUID().toString.take(8)

        val channel = pool.channel
        channel.queueDeclare(queue, false, false, true, null)
        pool.returnChannel(channel)

        (1 to 20).foreach { i =>
          val pubCh = pool.channel
          pubCh.basicPublish("", queue, null, s"msg-$i".getBytes)
          pool.returnChannel(pubCh)
        }

        val getCh    = pool.channel
        val messages = (1 to 20).map { _ =>
          val resp = getCh.basicGet(queue, true)
          resp should not be null
          new String(resp.getBody)
        }
        pool.returnChannel(getCh)

        messages shouldBe (1 to 20).map(i => s"msg-$i")
      } finally {
        pool.close()
      }
    }
  }
}
