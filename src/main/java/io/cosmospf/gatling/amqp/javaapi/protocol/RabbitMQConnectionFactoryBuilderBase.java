package io.cosmospf.gatling.amqp.javaapi.protocol;

import scala.Option;

public class RabbitMQConnectionFactoryBuilderBase {
    public RabbitMQConnectionFactoryBuilder host(String host) {
        return new RabbitMQConnectionFactoryBuilder(io.cosmospf.gatling.amqp.protocol.RabbitMQConnectionFactoryBuilder.apply(Option.apply(host), null, null,null,null));
    }
}
