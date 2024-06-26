package org.galaxio.gatling.amqp.javaapi.request;

import java.nio.charset.Charset;
import static io.gatling.javaapi.core.internal.Expressions.*;


public class PublishDslBuilderMessage {
    private final org.galaxio.gatling.amqp.request.PublishDslBuilderMessage wrapped;

    public PublishDslBuilderMessage(org.galaxio.gatling.amqp.request.PublishDslBuilderMessage wrapped){
        this.wrapped = wrapped;
    }
    public PublishDslBuilder textMessage(String text){
        return textMessage(text, io.gatling.core.Predef.configuration().core().charset());
    }
    public PublishDslBuilder textMessage(String text, Charset charset){
        return new PublishDslBuilder(wrapped.textMessage(toStringExpression(text), charset));
    }
}
