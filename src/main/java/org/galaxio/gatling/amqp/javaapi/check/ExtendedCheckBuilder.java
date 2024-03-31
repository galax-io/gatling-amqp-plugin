package org.galaxio.gatling.amqp.javaapi.check;

import org.galaxio.gatling.amqp.checks.AmqpResponseCodeCheckBuilder.*;
import org.galaxio.gatling.amqp.request.AmqpProtocolMessage;
import static scala.jdk.javaapi.CollectionConverters.*;
import java.util.Arrays;
import java.util.List;

public class ExtendedCheckBuilder{
    private final ExtendedDefaultFindCheckBuilder<AmqpMessageCheckType, AmqpProtocolMessage, String> wrapped;

    public ExtendedCheckBuilder(ExtendedDefaultFindCheckBuilder<AmqpMessageCheckType, AmqpProtocolMessage, String> wrapped){
        this.wrapped = wrapped;
    }

    public AmqpResponseCodeBuilder notIn(String... expected){
        return notIn(Arrays.asList(expected));
    }
    public AmqpResponseCodeBuilder notIn(List<String> expected){
        return new AmqpResponseCodeBuilder(wrapped.notIn(asScala(expected).toSeq()));
    }

}
