package org.galaxio.gatling.amqp.javaapi.protocol;

import scala.Option;
import scala.None$;

public class RabbitMQConnectionFactoryBuilderBase {
    @SuppressWarnings("unchecked")
    private static <T> Option<T> none() {
        return (Option<T>) None$.MODULE$;
    }

    public RabbitMQConnectionFactoryBuilder host(String host) {
        return new RabbitMQConnectionFactoryBuilder(
            org.galaxio.gatling.amqp.protocol.RabbitMQConnectionFactoryBuilder.apply(
                Option.apply(host), none(), none(), none(), none(),
                true, none(), true, none(), none(), none(), false, none()
            )
        );
    }
}
