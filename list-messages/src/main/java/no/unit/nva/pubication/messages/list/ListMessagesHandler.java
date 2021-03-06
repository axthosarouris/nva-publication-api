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
import no.unit.nva.publication.storage.model.MessageStatus;
import no.unit.nva.publication.storage.model.UserInstance;
import nva.commons.apigateway.ApiGatewayHandler;
import nva.commons.apigateway.RequestInfo;
import nva.commons.core.Environment;
import nva.commons.core.JacocoGenerated;

public class ListMessagesHandler extends ApiGatewayHandler<Void, ResourceConversation[]> {

    public static final String REQUESTED_ROLE = "role";
    public static final String EMPTY_STRING = "";
    public static final String CURATOR_ROLE = "Curator";
    private final MessageService messageService;

    @JacocoGenerated
    public ListMessagesHandler() {
        this(new Environment(), defaultMessageService());
    }

    public ListMessagesHandler(Environment environment, MessageService messageService) {
        super(Void.class, environment);
        this.messageService = messageService;
    }

    @Override
    protected ResourceConversation[] processInput(Void input, RequestInfo requestInfo, Context context) {
        UserInstance userInstance = extractUserInstanceFromRequest(requestInfo);
        if (userIsCurator(requestInfo)) {
            var result = messageService.listMessagesForCurator(userInstance.getOrganizationUri(), MessageStatus.UNREAD);
            return convertListToArray(result);
        } else {
            List<ResourceConversation> result = messageService.listMessagesForUser(userInstance);
            return convertListToArray(result);
        }
    }

    private boolean userIsCurator(RequestInfo requestInfo) {
        String assignedRolesToUser = requestInfo.getAssignedRoles().orElse(EMPTY_STRING);
        String roleRequestByTheUser = requestInfo.getQueryParameter(REQUESTED_ROLE);
        return CURATOR_ROLE.equals(roleRequestByTheUser) && assignedRolesToUser.contains(CURATOR_ROLE);
    }

    private UserInstance extractUserInstanceFromRequest(RequestInfo requestInfo) {
        String feideId = requestInfo.getFeideId().orElse(null);
        URI customerId = requestInfo.getCustomerId().map(URI::create).orElse(null);
        return new UserInstance(feideId, customerId);
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
        return result.toArray(ResourceConversation[]::new);
    }
}
