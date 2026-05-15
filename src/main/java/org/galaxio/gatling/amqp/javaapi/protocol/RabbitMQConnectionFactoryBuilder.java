package org.galaxio.gatling.amqp.javaapi.protocol;

import com.rabbitmq.client.ConnectionFactory;

public class RabbitMQConnectionFactoryBuilder{
    private org.galaxio.gatling.amqp.protocol.RabbitMQConnectionFactoryBuilder wrapped;

    public RabbitMQConnectionFactoryBuilder(org.galaxio.gatling.amqp.protocol.RabbitMQConnectionFactoryBuilder wrapped){
        this.wrapped = wrapped;
    }

    public RabbitMQConnectionFactoryBuilder username(String rabbitUsername){
        this.wrapped = wrapped.username(rabbitUsername);
        return this;
    }

    public RabbitMQConnectionFactoryBuilder password(String rabbitPassword){
        this.wrapped = wrapped.password(rabbitPassword);
        return this;
    }

    public RabbitMQConnectionFactoryBuilder port(Integer port){
        this.wrapped = wrapped.port(port);
        return this;
    }

    public RabbitMQConnectionFactoryBuilder vhost(String rabbitVHost){
        this.wrapped = wrapped.vhost(rabbitVHost);
        return this;
    }

    public RabbitMQConnectionFactoryBuilder automaticRecovery(Boolean enabled){
        this.wrapped = wrapped.automaticRecovery(enabled);
        return this;
    }

    public RabbitMQConnectionFactoryBuilder networkRecoveryInterval(Long millis){
        this.wrapped = wrapped.networkRecoveryInterval(millis);
        return this;
    }

    public RabbitMQConnectionFactoryBuilder topologyRecovery(Boolean enabled){
        this.wrapped = wrapped.topologyRecovery(enabled);
        return this;
    }

    public RabbitMQConnectionFactoryBuilder connectionTimeout(Integer millis){
        this.wrapped = wrapped.connectionTimeout(millis);
        return this;
    }

    public RabbitMQConnectionFactoryBuilder requestedHeartbeat(Integer seconds){
        this.wrapped = wrapped.requestedHeartbeat(seconds);
        return this;
    }

    public RabbitMQConnectionFactoryBuilder requestedChannelMax(Integer max){
        this.wrapped = wrapped.requestedChannelMax(max);
        return this;
    }

    public RabbitMQConnectionFactoryBuilder useSslProtocol(){
        this.wrapped = wrapped.useSslProtocol();
        return this;
    }

    public RabbitMQConnectionFactoryBuilder useSslProtocol(String protocol){
        this.wrapped = wrapped.useSslProtocol(protocol);
        return this;
    }

    public ConnectionFactory build(){
        return wrapped.build();
    }
}
