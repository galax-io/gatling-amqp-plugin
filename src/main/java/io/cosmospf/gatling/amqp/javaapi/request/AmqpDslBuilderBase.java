package io.cosmospf.gatling.amqp.javaapi.request;

public final class AmqpDslBuilderBase {
    private final io.cosmospf.gatling.amqp.request.AmqpDslBuilderBase wrapped;

    public AmqpDslBuilderBase(io.cosmospf.gatling.amqp.request.AmqpDslBuilderBase wrapped){
        this.wrapped = wrapped;
    }

    public PublishDslBuilderExchange publish(){
        return new PublishDslBuilderExchange(wrapped.publish(io.gatling.core.Predef.configuration()));
    }
    public RequestReplyDslBuilderExchange requestReply(){
        return new RequestReplyDslBuilderExchange(wrapped.requestReply(io.gatling.core.Predef.configuration()));
    }
}
