package org.galaxio.gatling.amqp.protocol

import com.rabbitmq.client.ConnectionFactory

case class RabbitMQConnectionFactoryBuilder(
    host: Option[String] = None,
    port: Option[Int] = None,
    username: Option[String] = None,
    password: Option[String] = None,
    virtualHost: Option[String] = None,
    automaticRecovery: Boolean = true,
    networkRecoveryInterval: Option[Long] = None,
    topologyRecovery: Boolean = true,
    connectionTimeout: Option[Int] = None,
    requestedHeartbeat: Option[Int] = None,
    requestedChannelMax: Option[Int] = None,
    useSsl: Boolean = false,
    sslProtocol: Option[String] = None,
) {

  def username(rabbitUsername: String): RabbitMQConnectionFactoryBuilder =
    this.copy(username = Some(rabbitUsername))
  def password(rabbitPassword: String): RabbitMQConnectionFactoryBuilder =
    this.copy(password = Some(rabbitPassword))

  def port(rabbitPort: Int): RabbitMQConnectionFactoryBuilder =
    this.copy(port = Some(rabbitPort))

  def vhost(rabbitVHost: String): RabbitMQConnectionFactoryBuilder =
    this.copy(virtualHost = Some(rabbitVHost))

  def automaticRecovery(enabled: Boolean): RabbitMQConnectionFactoryBuilder =
    this.copy(automaticRecovery = enabled)

  def networkRecoveryInterval(millis: Long): RabbitMQConnectionFactoryBuilder =
    this.copy(networkRecoveryInterval = Some(millis))

  def topologyRecovery(enabled: Boolean): RabbitMQConnectionFactoryBuilder =
    this.copy(topologyRecovery = enabled)

  def connectionTimeout(millis: Int): RabbitMQConnectionFactoryBuilder =
    this.copy(connectionTimeout = Some(millis))

  def requestedHeartbeat(seconds: Int): RabbitMQConnectionFactoryBuilder =
    this.copy(requestedHeartbeat = Some(seconds))

  def requestedChannelMax(max: Int): RabbitMQConnectionFactoryBuilder =
    this.copy(requestedChannelMax = Some(max))

  def useSslProtocol(): RabbitMQConnectionFactoryBuilder =
    this.copy(useSsl = true)

  def useSslProtocol(protocol: String): RabbitMQConnectionFactoryBuilder =
    this.copy(useSsl = true, sslProtocol = Some(protocol))

  def build: ConnectionFactory = {
    val cf = new ConnectionFactory()

    host.foreach(cf.setHost)
    port.foreach(cf.setPort)
    username.foreach(cf.setUsername)
    password.foreach(cf.setPassword)
    virtualHost.foreach(cf.setVirtualHost)

    cf.setAutomaticRecoveryEnabled(automaticRecovery)
    cf.setTopologyRecoveryEnabled(topologyRecovery)
    networkRecoveryInterval.foreach(cf.setNetworkRecoveryInterval)
    connectionTimeout.foreach(cf.setConnectionTimeout)
    requestedHeartbeat.foreach(cf.setRequestedHeartbeat)
    requestedChannelMax.foreach(cf.setRequestedChannelMax)

    if (useSsl) sslProtocol.fold(cf.useSslProtocol())(cf.useSslProtocol)

    cf
  }
}
