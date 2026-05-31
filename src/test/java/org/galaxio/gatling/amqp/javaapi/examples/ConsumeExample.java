package org.galaxio.gatling.amqp.javaapi.examples;

import org.galaxio.gatling.amqp.javaapi.protocol.AmqpProtocolBuilder;
import org.galaxio.gatling.amqp.javaapi.protocol.AmqpQueue;
import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;

import java.util.Map;

import static io.gatling.javaapi.core.CoreDsl.*;
import static org.galaxio.gatling.amqp.javaapi.AmqpDsl.*;

public class ConsumeExample extends Simulation {

    public AmqpProtocolBuilder amqpConf = amqp()
            .connectionFactory(
                    rabbitmq()
                            .host("localhost")
                            .port(5672)
                            .username("guest")
                            .password("guest")
                            .vhost("/")
                            .build()
            )
            .usePersistentDeliveryMode()
            .declare(new AmqpQueue("test_queue", false, false, false, Map.of()));

    public ScenarioBuilder scn = scenario("AMQP Consume test")
            .exec(
                    amqp("consume from queue")
                            .consume("test_queue")
                            .timeout(5000L)
                            .check(
                                    bodyString().exists()
                            )
            );

    {
        setUp(
                scn.injectOpen(atOnceUsers(1))
        )
                .protocols(amqpConf)
                .maxDuration(60);
    }
}
