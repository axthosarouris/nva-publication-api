package no.unit.nva.publication.service.impl;

import static no.unit.nva.publication.model.business.TicketEntry.createNewTicket;
import static no.unit.nva.publication.model.storage.Dao.CONTAINED_DATA_FIELD_NAME;
import static no.unit.nva.publication.model.storage.DynamoEntry.parseAttributeValuesMap;
import static no.unit.nva.publication.storage.model.DatabaseConstants.RESOURCES_TABLE_NAME;
import static nva.commons.core.attempt.Try.attempt;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.PutItemRequest;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import no.unit.nva.commons.json.JsonUtils;
import no.unit.nva.identifiers.SortableIdentifier;
import no.unit.nva.model.Publication;
import no.unit.nva.publication.model.business.Entity;
import no.unit.nva.publication.model.business.PublishingRequestCase;
import no.unit.nva.publication.model.business.TicketEntry;
import no.unit.nva.publication.model.business.UserInstance;
import no.unit.nva.publication.model.storage.PublishingRequestDao;
import no.unit.nva.publication.model.storage.TicketDao;
import nva.commons.apigateway.exceptions.ApiGatewayException;
import nva.commons.apigateway.exceptions.ConflictException;
import nva.commons.apigateway.exceptions.NotFoundException;
import nva.commons.core.JacocoGenerated;
import nva.commons.core.SingletonCollector;
import nva.commons.core.attempt.FunctionWithException;

public class PublishingRequestService extends ServiceWithTransactions {
    
    public static final String TICKET_NOT_FOUND_FOR_RESOURCE =
        "Could not find requested ticket for Resource: ";
    
    public static final String DOUBLE_QUOTES = "\"";
    public static final String EMPTY_STRING = "";
    private static final Supplier<SortableIdentifier> DEFAULT_IDENTIFIER_PROVIDER = SortableIdentifier::next;
    private final AmazonDynamoDB client;
    private final Clock clock;
    
    private final Supplier<SortableIdentifier> identifierProvider;
    private final ResourceService resourceService;
    
    public PublishingRequestService(AmazonDynamoDB client, Clock clock) {
        this(client, clock, DEFAULT_IDENTIFIER_PROVIDER);
    }
    
    protected PublishingRequestService(AmazonDynamoDB client,
                                       Clock clock,
                                       Supplier<SortableIdentifier> identifierProvider) {
        super();
        this.client = client;
        this.clock = clock;
        this.identifierProvider = identifierProvider;
        resourceService = new ResourceService(client, clock, identifierProvider);
    }
    
    public <T extends TicketEntry> T createTicket(TicketEntry ticketEntry, Class<T> ticketType)
        throws ApiGatewayException {
        //TODO: rename the method fetchPublication so that it reveals why we are fetching the publication.
        var associatedPublication = fetchPublication(ticketEntry);
        return createTicketForPublication(associatedPublication, ticketType);
    }
    
    public <T extends TicketEntry> T fetchTicket(TicketEntry dataEntry, Class<T> ticketType)
        throws NotFoundException {
        return fetchFromDatabase(dataEntry, ticketType)
            .map(TicketDao::getData)
            .map(ticketEntry -> (T) ticketEntry)
            .orElseThrow(() -> handleFetchPublishingRequestByResourceError(dataEntry.getIdentifier()));
    }
    
    public PublishingRequestCase updatePublishingRequest(PublishingRequestCase requestUpdate) {
        var entryUpdate = requestUpdate.copy();
        entryUpdate.setModifiedDate(clock.instant());
        entryUpdate.setVersion(Entity.nextVersion());
        var putItemRequest = cratePutItemRequest(entryUpdate);
        client.putItem(putItemRequest);
        return entryUpdate;
    }
    
    public TicketEntry fetchTicketByPublicationAndRequestIdentifiers(
        SortableIdentifier publicationIdentifier,
        SortableIdentifier ticketIdentifier)
        throws NotFoundException {
        var publication = resourceService.getPublicationByIdentifier(publicationIdentifier);
        var userInstance = UserInstance.fromPublication(publication);
        var queryObject = PublishingRequestCase.createQuery(userInstance,
            publicationIdentifier,
            ticketIdentifier);
        return fetchTicket(queryObject, PublishingRequestCase.class);
    }
    
    public <T extends TicketEntry> TicketEntry getTicketByResourceIdentifier(URI customerId,
                                                                             SortableIdentifier resourceIdentifier,
                                                                             Class<T> ticketType) {
        
        var query = TicketDao.queryByCustomerAndResource(customerId, resourceIdentifier, ticketType);
        
        var queryResult = client.query(query);
        return queryResult.getItems().stream()
            .map(item -> parseAttributeValuesMap(item, PublishingRequestDao.class))
            .map(PublishingRequestDao::getData)
            .collect(SingletonCollector.tryCollect())
            .orElseThrow();
    }
    
    @Override
    protected AmazonDynamoDB getClient() {
        return client;
    }
    
    @Override
    @JacocoGenerated
    protected Clock getClock() {
        return clock;
    }
    
    private static NotFoundException handleFetchPublishingRequestByResourceError(
        SortableIdentifier resourceIdentifier) {
        return new NotFoundException(TICKET_NOT_FOUND_FOR_RESOURCE + resourceIdentifier.toString());
    }
    
    private PutItemRequest cratePutItemRequest(PublishingRequestCase entryUpdate) {
        var dao = new PublishingRequestDao(entryUpdate);
        var condition = new UpdateCaseButNotOwnerCondition(entryUpdate);
        
        return new PutItemRequest()
            .withTableName(RESOURCES_TABLE_NAME)
            .withItem(dao.toDynamoFormat())
            .withConditionExpression(condition.getConditionExpression())
            .withExpressionAttributeNames(condition.getExpressionAttributeNames())
            .withExpressionAttributeValues(condition.getExpressionAttributeValues());
    }
    
    private Publication fetchPublication(TicketEntry ticketEntry)
        throws ApiGatewayException {
        var userInstance = UserInstance.create(ticketEntry.getOwner(), ticketEntry.getCustomerId());
        return resourceService.getPublication(userInstance, ticketEntry.getResourceIdentifier());
    }
    
    //TODO: try to remove suppression.
    @SuppressWarnings("unchecked")
    private <T extends TicketEntry> T createTicketForPublication(Publication publication, Class<T> ticketType)
        throws ConflictException, NotFoundException {
        //TODO: Do something about the clock and identifier provider dependencies, if possible.
        var ticketEntry = createNewTicket(publication, ticketType, clock, identifierProvider);
        var request = ticketEntry.toDao().createInsertionTransactionRequest();
        sendTransactionWriteRequest(request);
        FunctionWithException<TicketEntry, TicketEntry, NotFoundException>
            fetchTicketProvider = dataEntry -> fetchTicket(dataEntry, ticketType);
        return (T) fetchEventualConsistentDataEntry(ticketEntry, fetchTicketProvider).orElseThrow();
    }
    
    private <T extends TicketEntry> Optional<TicketDao> fetchFromDatabase(TicketEntry queryObject,
                                                                          Class<T> ticketType) {
        var queryDao = TicketDao.queryObject(queryObject, ticketType);
        return queryDao.fetchItem(client);
    }
    
    private static class UpdateCaseButNotOwnerCondition {
        
        private String conditionExpression;
        private Map<String, String> expressionAttributeNames;
        private Map<String, AttributeValue> expressionAttributeValues;
        
        public UpdateCaseButNotOwnerCondition(PublishingRequestCase entryUpdate) {
            createCondition(entryUpdate);
        }
        
        public String getConditionExpression() {
            return conditionExpression;
        }
        
        public Map<String, String> getExpressionAttributeNames() {
            return expressionAttributeNames;
        }
        
        public Map<String, AttributeValue> getExpressionAttributeValues() {
            return expressionAttributeValues;
        }
        
        private void createCondition(PublishingRequestCase entryUpdate) {
            
            this.expressionAttributeNames = Map.of(
                "#data", CONTAINED_DATA_FIELD_NAME,
                "#createdDate", PublishingRequestCase.CREATED_DATE_FIELD,
                "#customerId", PublishingRequestCase.CUSTOMER_ID_FIELD,
                "#identifier", PublishingRequestCase.IDENTIFIER_FIELD,
                "#modifiedDate", PublishingRequestCase.MODIFIED_DATE_FIELD,
                "#owner", PublishingRequestCase.OWNER_FIELD,
                "#resourceIdentifier", PublishingRequestCase.RESOURCE_IDENTIFIER_FIELD,
                "#version", Entity.VERSION);
            
            this.expressionAttributeValues =
                Map.of(
                    ":createdDate", new AttributeValue(dateAsString(entryUpdate.getCreatedDate())),
                    ":customerId", new AttributeValue(entryUpdate.getCustomerId().toString()),
                    ":identifier", new AttributeValue(entryUpdate.getIdentifier().toString()),
                    ":modifiedDate", new AttributeValue(dateAsString(entryUpdate.getModifiedDate())),
                    ":owner", new AttributeValue(entryUpdate.getOwner()),
                    ":resourceIdentifier", new AttributeValue(entryUpdate.getResourceIdentifier().toString()),
                    ":version", new AttributeValue(entryUpdate.getVersion().toString()));
            
            this.conditionExpression = "#data.#createdDate = :createdDate "
                                       + "AND #data.#customerId = :customerId "
                                       + "AND #data.#identifier = :identifier "
                                       + "AND #data.#modifiedDate <> :modifiedDate "
                                       + "AND #data.#owner = :owner "
                                       + "AND #data.#resourceIdentifier = :resourceIdentifier "
                                       + "AND #data.#version <> :version ";
        }
        
        private String dateAsString(Instant date) {
            return attempt(() -> JsonUtils.dtoObjectMapper.writeValueAsString(date))
                .map(dateStr -> dateStr.replace(DOUBLE_QUOTES, EMPTY_STRING))
                .orElseThrow();
        }
    }
}