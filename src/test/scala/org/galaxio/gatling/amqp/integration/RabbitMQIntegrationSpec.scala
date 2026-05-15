package org.galaxio.gatling.amqp.integration

import com.dimafeng.testcontainers.{ForAllTestContainer, RabbitMQContainer}
import com.rabbitmq.client.{ConnectionFactory, Delivery}
import org.galaxio.gatling.amqp.client.AmqpConnectionPool
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
  }
}
