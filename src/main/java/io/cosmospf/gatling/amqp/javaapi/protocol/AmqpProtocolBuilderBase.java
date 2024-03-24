package io.cosmospf.gatling.amqp.javaapi.protocol;

import com.rabbitmq.client.ConnectionFactory;

public class AmqpProtocolBuilderBase {

    public AmqpProtocolBuilder connectionFactory(ConnectionFactory cf){
        return new AmqpProtocolBuilder(io.cosmospf.gatling.amqp.protocol.AmqpProtocolBuilderBase.connectionFactory(cf));
    }

    public AmqpProtocolBuilder connectionFactory(ConnectionFactory requestConnectionFactory, ConnectionFactory replyConnectionFactory){
        return new AmqpProtocolBuilder(io.cosmospf.gatling.amqp.protocol.AmqpProtocolBuilderBase.connectionFactory(requestConnectionFactory, replyConnectionFactory));
    }
}
