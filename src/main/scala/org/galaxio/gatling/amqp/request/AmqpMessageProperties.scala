package org.galaxio.gatling.amqp.request

import java.util.Date

import com.rabbitmq.client.AMQP
import io.gatling.commons.validation._
import io.gatling.core.session.{Expression, Session}

import scala.jdk.CollectionConverters._

case class AmqpMessageProperties(
    contentType: Option[Expression[String]] = None,
    contentEncoding: Option[Expression[String]] = None,
    headers: Map[String, Expression[AnyRef]] = Map.empty,
    deliveryMode: Option[Expression[Int]] = None,
    priority: Option[Expression[Int]] = None,
    correlationId: Option[Expression[String]] = None,
    replyTo: Option[Expression[String]] = None,
    expiration: Option[Expression[String]] = None,
    messageId: Option[Expression[String]] = None,
    timestamp: Option[Expression[Date]] = None,
    `type`: Option[Expression[String]] = None,
    userId: Option[Expression[String]] = None,
    appId: Option[Expression[String]] = None,
    clusterId: Option[Expression[String]] = None,
)

object AmqpMessageProperties {

  private implicit class OptExpressionUtil[T](val optExp: Option[Expression[T]]) extends AnyVal {
    def apply(
        session: Session,
        p: AMQP.BasicProperties.Builder,
        setProperty: T => AMQP.BasicProperties.Builder,
    ): Validation[AMQP.BasicProperties.Builder] =
      optExp.fold(p.success)(_(session).map(setProperty))
  }

  def toBasicProperties(p: AmqpMessageProperties, s: Session): Validation[AMQP.BasicProperties] = {
    val bp = new AMQP.BasicProperties().builder()
    import p._
    for {
      b  <- contentType(s, bp, bp.contentType)
      b  <- contentEncoding(s, b, b.contentEncoding)
      b  <- deliveryMode(s, b, i => b.deliveryMode(i))
      b  <- priority(s, b, i => b.priority(i))
      b  <- correlationId(s, b, b.correlationId)
      b  <- replyTo(s, b, b.replyTo)
      b  <- expiration(s, b, b.expiration)
      b  <- messageId(s, b, b.messageId)
      b  <- timestamp(s, b, b.timestamp)
      b  <- `type`(s, b, b.`type`)
      b  <- userId(s, b, b.userId)
      b  <- appId(s, b, b.appId)
      b  <- clusterId(s, b, b.clusterId)
      rh <- headers
              .foldLeft(Map.empty[String, AnyRef].success) { case (resolvedHeaders, (key, value)) =>
                for {
                  v  <- value(s)
                  rh <- resolvedHeaders
                } yield rh + (key -> v)
              }
    } yield b.headers(rh.asJava).build()

  }
}
