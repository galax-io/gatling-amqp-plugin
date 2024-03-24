package io.cosmospf.gatling.amqp.javaapi;

import static io.gatling.javaapi.core.internal.Expressions.*;

import io.cosmospf.gatling.amqp.javaapi.check.AmqpChecks;
import io.cosmospf.gatling.amqp.javaapi.check.ExtendedCheckBuilder;
import io.cosmospf.gatling.amqp.javaapi.protocol.AmqpProtocolBuilderBase;
import io.cosmospf.gatling.amqp.javaapi.protocol.RabbitMQConnectionFactoryBuilderBase;
import io.cosmospf.gatling.amqp.javaapi.request.AmqpDslBuilderBase;
import io.cosmospf.gatling.amqp.checks.AmqpResponseCodeCheckBuilder;
import io.cosmospf.gatling.amqp.request.AmqpProtocolMessage;
import io.cosmospf.gatling.amqp.javaapi.check.AmqpChecks;
import io.cosmospf.gatling.amqp.javaapi.protocol.*;
import scala.Function1;

public final class AmqpDsl {

    public static AmqpProtocolBuilderBase amqp() {
        return new AmqpProtocolBuilderBase();
    }

    public static AmqpDslBuilderBase amqp(String requestName) {
        return new AmqpDslBuilderBase(io.cosmospf.gatling.amqp.Predef.amqp(toStringExpression(requestName)));
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
