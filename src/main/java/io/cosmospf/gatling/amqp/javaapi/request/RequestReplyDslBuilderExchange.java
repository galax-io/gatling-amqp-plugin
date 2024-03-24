package io.cosmospf.gatling.amqp.javaapi.request;

import static io.gatling.javaapi.core.internal.Expressions.*;

public class RequestReplyDslBuilderExchange {
    private final io.cosmospf.gatling.amqp.request.RequestReplyDslBuilderExchange wrapped;

    public RequestReplyDslBuilderExchange(io.cosmospf.gatling.amqp.request.RequestReplyDslBuilderExchange wrapped) {
        this.wrapped = wrapped;
    }

    public RequestReplyDslBuilderMessage directExchange(String name, String routingKey) {
        return new RequestReplyDslBuilderMessage(wrapped.directExchange(toStringExpression(name), toStringExpression(routingKey)));
    }

    public RequestReplyDslBuilderMessage topicExchange(String name, String routingKey) {
        return new RequestReplyDslBuilderMessage(wrapped.topicExchange(toStringExpression(name), toStringExpression(routingKey)));
    }

    public RequestReplyDslBuilderMessage queueExchange(String name) {
        return new RequestReplyDslBuilderMessage(wrapped.queueExchange(toStringExpression(name)));
    }

}
