package org.galaxio.gatling.amqp.examples.utils

import com.rabbitmq.client._

import java.nio.charset.StandardCharsets
import scala.util.Try

/** Simple RabbitMQClient which consumes messages from one broker and write them to other broker.
  */
object SimpleRabbitMQClient {
  private val readQueue = "readQueue"
  private val readPort  = 5672

  private val writeQueue = "writeQueue"
  private val writePort  = 5673

  private var readConnection: Connection  = _
  private var readChannel: Channel        = _
  private var writeConnection: Connection = _
  private var writeChannel: Channel       = _

  val deliverCallback: DeliverCallback = { (_: String, message: Delivery) =>
    writeChannel.queueDeclare(writeQueue, true, false, false, null)
    writeChannel.basicPublish("", writeQueue, message.getProperties, "Message processed".getBytes(StandardCharsets.UTF_8))
  }

  val cancelCallback: CancelCallback = (_: String) => {}

  def readAndWrite(): String = {
    readChannel.queueDeclare(readQueue, true, false, false, null)
    readChannel.basicConsume(readQueue, true, deliverCallback, cancelCallback)
  }

  def tearDown(): Unit = {
    Try(readChannel.queueDelete(readQueue))
    Try(writeChannel.queueDelete(writeQueue))
    Try(readChannel.close())
    Try(writeChannel.close())
    Try(readConnection.close())
    Try(writeConnection.close())
  }

  def setup(): Unit = {
    readConnection = getConnection(readPort)
    readChannel = readConnection.createChannel()
    writeConnection = getConnection(writePort)
    writeChannel = writeConnection.createChannel()
    readChannel.queueDeclare(readQueue, true, false, false, null)
    writeChannel.queueDeclare(writeQueue, true, false, false, null)
  }

  private def getConnection(port: Int): Connection = {
    val connectionFactory = new ConnectionFactory()
    connectionFactory.setHost("localhost")
    connectionFactory.setPort(port)
    connectionFactory.newConnection()
  }

}
