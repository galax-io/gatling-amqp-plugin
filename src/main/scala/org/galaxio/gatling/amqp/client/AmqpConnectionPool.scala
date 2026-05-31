package org.galaxio.gatling.amqp.client

import java.util.concurrent.Executors

import com.rabbitmq.client.{Channel, Connection, ConnectionFactory}
import org.apache.commons.pool2.ObjectPool
import org.apache.commons.pool2.impl.{GenericObjectPool, GenericObjectPoolConfig}

class AmqpConnectionPool(
    factory: ConnectionFactory,
    consumerThreadsCount: Int,
    channelPoolSize: Int = 16,
    publisherConfirms: Boolean = false,
) {

  private val connection: Connection = factory.newConnection(Executors.newFixedThreadPool(consumerThreadsCount))

  private val poolConfig = new GenericObjectPoolConfig[Channel]()
  poolConfig.setMaxTotal(channelPoolSize)
  poolConfig.setTestOnBorrow(true)
  poolConfig.setTestOnReturn(true)
  poolConfig.setTimeBetweenEvictionRuns(java.time.Duration.ofSeconds(30))
  poolConfig.setMinEvictableIdleDuration(java.time.Duration.ofMinutes(5))

  private val channelPool: ObjectPool[Channel] =
    new GenericObjectPool[Channel](new AmqpChannelFactory(connection, publisherConfirms), poolConfig)

  def createConsumerChannel: Channel = connection.createChannel()

  def close(): Unit = {
    if (connection != null) {
      channelPool.close()
      if (connection.isOpen) {
        connection.close()
      }
    }
  }

  def channel: Channel                 = {
    channelPool.borrowObject()
  }
  def returnChannel(ch: Channel): Unit =
    if (ch.isOpen) channelPool.returnObject(ch)
    else channelPool.invalidateObject(ch)

  def invalidate(ch: Channel): Unit = channelPool.invalidateObject(ch)
}
