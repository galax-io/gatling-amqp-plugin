package org.galaxio.gatling.amqp.integration

import com.dimafeng.testcontainers.{ForAllTestContainer, RabbitMQContainer}
import com.rabbitmq.client.ConnectionFactory
import org.galaxio.gatling.amqp.client.AmqpConnectionPool
import org.galaxio.gatling.amqp.tags.DockerTest
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.testcontainers.utility.DockerImageName

class ChannelPoolRecoverySpec extends AnyWordSpec with Matchers with ForAllTestContainer {

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

  "Channel pool validation" should {
    "not return a closed channel on borrow" taggedAs DockerTest in {
      val pool = new AmqpConnectionPool(connectionFactory, 2, 4)
      try {
        // Borrow a channel and close it manually to simulate broker-side closure
        val ch = pool.channel
        ch.isOpen shouldBe true
        ch.close()
        ch.isOpen shouldBe false

        // Return the closed channel; pool should invalidate it
        pool.returnChannel(ch)

        // Next borrow should get a fresh, open channel
        val ch2 = pool.channel
        ch2.isOpen shouldBe true
        pool.returnChannel(ch2)
      } finally {
        pool.close()
      }
    }

    "recover after multiple channels are closed" taggedAs DockerTest in {
      val pool = new AmqpConnectionPool(connectionFactory, 2, 4)
      try {
        // Borrow several channels and close them to poison the pool
        val channels = (1 to 3).map(_ => pool.channel)
        channels.foreach(_.isOpen shouldBe true)

        // Simulate broker-side closure
        channels.foreach(_.close())
        channels.foreach(ch => pool.returnChannel(ch))

        // All subsequent borrows should return open channels
        val freshChannels = (1 to 3).map { _ =>
          val ch = pool.channel
          ch.isOpen shouldBe true
          ch
        }
        freshChannels.foreach(pool.returnChannel)
      } finally {
        pool.close()
      }
    }

    "invalidate explicitly closed channel" taggedAs DockerTest in {
      val pool = new AmqpConnectionPool(connectionFactory, 2, 4)
      try {
        val ch = pool.channel
        ch.isOpen shouldBe true
        ch.close()

        // Explicit invalidation should not throw
        pool.invalidate(ch)

        // Pool should create a new valid channel
        val ch2 = pool.channel
        ch2.isOpen shouldBe true
        pool.returnChannel(ch2)
      } finally {
        pool.close()
      }
    }
  }
}
