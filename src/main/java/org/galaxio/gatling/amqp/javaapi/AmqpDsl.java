package org.galaxio.gatling.amqp.javaapi;

import static io.gatling.javaapi.core.internal.Expressions.*;

import org.galaxio.gatling.amqp.javaapi.check.ExtendedCheckBuilder;
import org.galaxio.gatling.amqp.javaapi.protocol.AmqpProtocolBuilderBase;
import org.galaxio.gatling.amqp.javaapi.protocol.RabbitMQConnectionFactoryBuilderBase;
import org.galaxio.gatling.amqp.javaapi.request.AmqpDslBuilderBase;
import org.galaxio.gatling.amqp.checks.AmqpResponseCodeCheckBuilder;
import org.galaxio.gatling.amqp.request.AmqpProtocolMessage;
import org.galaxio.gatling.amqp.javaapi.check.AmqpChecks;
import org.galaxio.gatling.amqp.javaapi.protocol.*;
import org.galaxio.gatling.amqp.javaapi.check.AmqpChecks;
import scala.Function1;

public final class AmqpDsl {

    public static AmqpProtocolBuilderBase amqp() {
        return new AmqpProtocolBuilderBase();
    }

    public static AmqpDslBuilderBase amqp(String requestName) {
        return new AmqpDslBuilderBase(org.galaxio.gatling.amqp.Predef.amqp(toStringExpression(requestName)));
    }

    public static RabbitMQConnectionFactoryBuilderBase rabbitmq() {
        return new RabbitMQConnectionFactoryBuilderBase();
    }

    public static AmqpChecks.AmqpCheckTypeWrapper simpleCheck(Function1<AmqpProtocolMessage, Boolean> f) {
        return new AmqpChecks.AmqpCheckTypeWrapper(new AmqpChecks.SimpleChecksScala().simpleCheck(f.andThen(Boolean::valueOf)));
    }

    public static ExtendedCheckBuilder responseCode() {
        return new ExtendedCheckBuilder(AmqpResponseCodeCheckBuilder.ResponseCode());
    }
}
