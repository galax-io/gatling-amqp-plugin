package org.galaxio.gatling.amqp.integration

import com.dimafeng.testcontainers.{ForAllTestContainer, RabbitMQContainer}
import com.rabbitmq.client.{BuiltinExchangeType, ConnectionFactory, Delivery}
import org.galaxio.gatling.amqp.client.AmqpConnectionPool
import org.galaxio.gatling.amqp.protocol._
import org.galaxio.gatling.amqp.tags.DockerTest
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.testcontainers.utility.DockerImageName

import java.util.concurrent.{CountDownLatch, TimeUnit}

class RabbitMQIntegrationSpec extends AnyWordSpec with Matchers with ForAllTestContainer with BeforeAndAfterAll {

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

  "RabbitMQ integration" should {
    "publish and consume a message" taggedAs DockerTest in {
      val conn    = connectionFactory.newConnection()
      val channel = conn.createChannel()
      try {
        channel.queueDeclare("test_publish", false, false, true, null)
        channel.basicPublish("", "test_publish", null, "hello".getBytes)

        val response = channel.basicGet("test_publish", true)
        response should not be null
        new String(response.getBody) shouldBe "hello"
      } finally {
        channel.close()
        conn.close()
      }
    }

    "connection pool borrows and returns channels" taggedAs DockerTest in {
      val pool = new AmqpConnectionPool(connectionFactory, 2, 4)
      try {
        val ch1 = pool.channel
        val ch2 = pool.channel
        ch1.isOpen shouldBe true
        ch2.isOpen shouldBe true
        pool.returnChannel(ch1)
        pool.returnChannel(ch2)

        val ch3 = pool.channel
        ch3.isOpen shouldBe true
        pool.returnChannel(ch3)
      } finally {
        pool.close()
      }
    }

    "connection pool close shuts down cleanly" taggedAs DockerTest in {
      val pool = new AmqpConnectionPool(connectionFactory, 2, 4)
      val ch   = pool.channel
      pool.returnChannel(ch)
      pool.close()
    }

    "publish via exchange and consume with routing key" taggedAs DockerTest in {
      val conn    = connectionFactory.newConnection()
      val channel = conn.createChannel()
      try {
        channel.exchangeDeclare("test_topic", "topic", false, true, null)
        val queueName = channel.queueDeclare().getQueue
        channel.queueBind(queueName, "test_topic", "test.#")

        channel.basicPublish("test_topic", "test.key", null, "routed message".getBytes)

        val response = channel.basicGet(queueName, true)
        response should not be null
        new String(response.getBody) shouldBe "routed message"
      } finally {
        channel.close()
        conn.close()
      }
    }

    "async consumer receives messages" taggedAs DockerTest in {
      val conn    = connectionFactory.newConnection()
      val channel = conn.createChannel()
      try {
        channel.queueDeclare("test_async", false, false, true, null)

        val latch           = new CountDownLatch(1)
        var receivedMessage = ""

        channel.basicConsume(
          "test_async",
          true,
          (_: String, delivery: Delivery) => {
            receivedMessage = new String(delivery.getBody)
            latch.countDown()
          },
          (_: String) => (),
        )

        channel.basicPublish("", "test_async", null, "async hello".getBytes)
        latch.await(10, TimeUnit.SECONDS) shouldBe true
        receivedMessage shouldBe "async hello"
      } finally {
        channel.close()
        conn.close()
      }
    }

    "init actions do not deplete channel pool" taggedAs DockerTest in {
      val poolSize = 2
      val pool     = new AmqpConnectionPool(connectionFactory, 2, poolSize)
      try {
        // Simulate init action pattern: borrow, use for declarations, return
        val ch = pool.channel
        ch.exchangeDeclare("init_test_exchange", "topic", false, true, null)
        ch.queueDeclare("init_test_queue", false, false, true, null)
        ch.queueBind("init_test_queue", "init_test_exchange", "test.#")
        pool.returnChannel(ch)

        // All pool channels should still be available
        val borrowed = (1 to poolSize).map(_ => pool.channel)
        borrowed.foreach(_.isOpen shouldBe true)
        borrowed.foreach(pool.returnChannel)
      } finally {
        pool.close()
      }
    }

    "init action failure invalidates channel without depleting pool" taggedAs DockerTest in {
      val poolSize = 2
      val pool     = new AmqpConnectionPool(connectionFactory, 2, poolSize)
      try {
        val ch = pool.channel
        // Force a channel error by using a passive declare on a non-existent queue
        try {
          ch.queueDeclarePassive("non_existent_queue_xyz_" + System.nanoTime())
        } catch {
          case _: Exception =>
            pool.invalidate(ch)
        }

        // Pool should still be usable at full capacity
        val borrowed = (1 to poolSize).map(_ => pool.channel)
        borrowed.foreach(_.isOpen shouldBe true)
        borrowed.foreach(pool.returnChannel)
      } finally {
        pool.close()
      }
    }

    "multiple messages in sequence" taggedAs DockerTest in {
      val conn    = connectionFactory.newConnection()
      val channel = conn.createChannel()
      try {
        channel.queueDeclare("test_multi", false, false, true, null)

        (1 to 5).foreach { i =>
          channel.basicPublish("", "test_multi", null, s"msg-$i".getBytes)
        }

        val messages = (1 to 5).map { _ =>
          val resp = channel.basicGet("test_multi", true)
          resp should not be null
          new String(resp.getBody)
        }

        messages shouldBe (1 to 5).map(i => s"msg-$i")
      } finally {
        channel.close()
        conn.close()
      }
    }

    "publisher confirms acknowledge published messages" taggedAs DockerTest in {
      val pool = new AmqpConnectionPool(connectionFactory, 2, 4, publisherConfirms = true)
      try {
        val ch = pool.channel
        ch.queueDeclare("test_confirms", false, false, true, null)
        ch.basicPublish("", "test_confirms", null, "confirmed msg".getBytes)
        ch.waitForConfirmsOrDie(5000)
        pool.returnChannel(ch)

        // Verify the message actually arrived in the queue
        val conn   = connectionFactory.newConnection()
        val readCh = conn.createChannel()
        try {
          val response = readCh.basicGet("test_confirms", true)
          response should not be null
          new String(response.getBody) shouldBe "confirmed msg"
        } finally {
          readCh.close()
          conn.close()
        }
      } finally {
        pool.close()
      }
    }

    "channel invalidation recovery provides a fresh working channel" taggedAs DockerTest in {
      val poolSize = 4
      val pool     = new AmqpConnectionPool(connectionFactory, 2, poolSize)
      try {
        val ch = pool.channel
        // Force a channel error via passive declare of a non-existent queue
        try {
          ch.queueDeclarePassive("non_existent_queue_recovery_" + System.nanoTime())
        } catch {
          case _: Exception =>
            pool.invalidate(ch)
        }

        // Borrow a new channel — it must be open and functional
        val freshCh = pool.channel
        freshCh.isOpen shouldBe true

        // Verify the fresh channel can perform real broker operations
        freshCh.queueDeclare("test_recovery", false, false, true, null)
        freshCh.basicPublish("", "test_recovery", null, "recovered".getBytes)
        val response = freshCh.basicGet("test_recovery", true)
        response should not be null
        new String(response.getBody) shouldBe "recovered"
        pool.returnChannel(freshCh)
      } finally {
        pool.close()
      }
    }

    "two-pool request-reply architecture" taggedAs DockerTest in {
      val requestPool = new AmqpConnectionPool(connectionFactory, 2, 4)
      val replyPool   = new AmqpConnectionPool(connectionFactory, 2, 4)
      try {
        // Declare a shared queue via the request pool
        val pubCh = requestPool.channel
        pubCh.queueDeclare("test_two_pool", false, false, true, null)
        pubCh.basicPublish("", "test_two_pool", null, "request payload".getBytes)
        requestPool.returnChannel(pubCh)

        // Consume the message via the reply pool
        val consCh   = replyPool.channel
        val latch    = new CountDownLatch(1)
        var received = ""

        consCh.basicConsume(
          "test_two_pool",
          true,
          (_: String, delivery: Delivery) => {
            received = new String(delivery.getBody)
            latch.countDown()
          },
          (_: String) => (),
        )

        latch.await(10, TimeUnit.SECONDS) shouldBe true
        received shouldBe "request payload"
        replyPool.returnChannel(consCh)
      } finally {
        requestPool.close()
        replyPool.close()
      }
    }
  }
}
