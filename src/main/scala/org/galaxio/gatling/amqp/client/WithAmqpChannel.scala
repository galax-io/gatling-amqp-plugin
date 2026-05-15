package org.galaxio.gatling.amqp.client

import com.rabbitmq.client.{AlreadyClosedException, Channel, ShutdownSignalException}

trait WithAmqpChannel {
  protected val pool: AmqpConnectionPool
  def withChannel[T](channelAction: Channel => T): T = {
    val ch = pool.channel
    try {
      val result = channelAction(ch)
      pool.returnChannel(ch)
      result
    } catch {
      case e @ (_: AlreadyClosedException | _: ShutdownSignalException) =>
        pool.invalidate(ch)
        throw e
      case e: Throwable                                                 =>
        pool.returnChannel(ch)
        throw e
    }
  }

}
