package no.unit.nva.pubication.messages.list;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.lambda.runtime.Context;
import java.net.HttpURLConnection;
import java.net.URI;
import java.time.Clock;
import java.util.List;
import no.unit.nva.publication.service.impl.MessageService;
import no.unit.nva.publication.service.impl.ResourceConversation;
import no.unit.nva.publication.storage.model.UserInstance;
import nva.commons.apigateway.ApiGatewayHandler;
import nva.commons.apigateway.RequestInfo;
import nva.commons.core.Environment;
import nva.commons.core.JacocoGenerated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ListMessagesHandler extends ApiGatewayHandler<Void, ResourceConversation[]> {

    public static final Logger LOGGER = LoggerFactory.getLogger(ListMessagesHandler.class);
    private final MessageService messageService;

    @JacocoGenerated
    public ListMessagesHandler() {
        this(new Environment(), defaultMessageService());
    }

    public ListMessagesHandler(Environment environment, MessageService messageService) {
        super(Void.class, environment, LOGGER);
        this.messageService = messageService;
    }

    @Override
    protected ResourceConversation[] processInput(Void input, RequestInfo requestInfo, Context context) {
        String feideId = requestInfo.getFeideId().orElse(null);
        URI customerId = requestInfo.getCustomerId().map(URI::create).orElse(null);
        UserInstance userInstance = new UserInstance(feideId, customerId);
        List<ResourceConversation> result = messageService.listMessagesForUser(userInstance);
        return convertListToArray(result);
    }

    @Override
    protected Integer getSuccessStatusCode(Void input, ResourceConversation[] output) {
        return HttpURLConnection.HTTP_OK;
    }

    @JacocoGenerated
    private static MessageService defaultMessageService() {
        AmazonDynamoDB client = AmazonDynamoDBClientBuilder.defaultClient();
        return new MessageService(client, Clock.systemDefaultZone());
    }

    private ResourceConversation[] convertListToArray(List<ResourceConversation> result) {
        ResourceConversation[] resultArray = new ResourceConversation[result.size()];
        result.toArray(resultArray);
        return resultArray;
    }
}
