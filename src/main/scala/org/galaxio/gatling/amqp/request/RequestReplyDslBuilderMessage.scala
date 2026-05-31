package org.galaxio.gatling.amqp.request

import io.gatling.core.config.GatlingConfiguration
import io.gatling.core.session.Expression
import org.galaxio.gatling.amqp.action.RequestReplyBuilder

import java.nio.charset.Charset

case class RequestReplyDslBuilderMessage(
    requestName: Expression[String],
    destination: AmqpExchange,
    replyDest: AmqpExchange,
    configuration: GatlingConfiguration,
) {

  /** Set the reply queue where the plugin listens for responses.
    */
  def replyExchange(name: Expression[String]): RequestReplyDslBuilderMessage = replyDestination(AmqpQueueExchange(name))
  private def replyDestination(destination: AmqpExchange)                    = this.copy(replyDest = destination)

  def textMessage(text: Expression[String], charset: Charset = configuration.core.charset): RequestReplyDslBuilder =
    message(TextAmqpMessage(text, charset))

  def bytesMessage(bytes: Expression[Array[Byte]]): RequestReplyDslBuilder = message(BytesAmqpMessage(bytes))

  private def message(mess: AmqpMessage) =
    RequestReplyDslBuilder(
      AmqpAttributes(requestName, destination, mess),
      RequestReplyBuilder(_, replyDest, configuration),
    )
}
