package no.unit.nva.publication.service.impl;

import static java.util.Objects.isNull;
import static no.unit.nva.publication.model.business.DoiRequest.RESOURCE_STATUS_FIELD;
import static no.unit.nva.publication.model.business.TicketEntry.Constants.MODIFIED_DATE_FIELD;
import static no.unit.nva.publication.model.business.TicketEntry.Constants.STATUS_FIELD;
import static no.unit.nva.publication.model.storage.Dao.CONTAINED_DATA_FIELD_NAME;
import static no.unit.nva.publication.model.storage.DoiRequestDao.queryObject;
import static no.unit.nva.publication.model.storage.DynamoEntry.parseAttributeValuesMap;
import static no.unit.nva.publication.storage.model.DatabaseConstants.BY_CUSTOMER_RESOURCE_INDEX_NAME;
import static no.unit.nva.publication.storage.model.DatabaseConstants.BY_CUSTOMER_RESOURCE_INDEX_PARTITION_KEY_NAME;
import static no.unit.nva.publication.storage.model.DatabaseConstants.BY_CUSTOMER_RESOURCE_INDEX_SORT_KEY_NAME;
import static no.unit.nva.publication.storage.model.DatabaseConstants.BY_TYPE_CUSTOMER_STATUS_INDEX_NAME;
import static no.unit.nva.publication.storage.model.DatabaseConstants.BY_TYPE_CUSTOMER_STATUS_INDEX_PARTITION_KEY_NAME;
import static no.unit.nva.publication.storage.model.DatabaseConstants.BY_TYPE_CUSTOMER_STATUS_INDEX_SORT_KEY_NAME;
import static no.unit.nva.publication.storage.model.DatabaseConstants.RESOURCES_TABLE_NAME;
import static nva.commons.core.attempt.Try.attempt;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ConditionalCheckFailedException;
import com.amazonaws.services.dynamodbv2.model.GetItemRequest;
import com.amazonaws.services.dynamodbv2.model.GetItemResult;
import com.amazonaws.services.dynamodbv2.model.QueryRequest;
import com.amazonaws.services.dynamodbv2.model.QueryResult;
import com.amazonaws.services.dynamodbv2.model.ReturnValue;
import com.amazonaws.services.dynamodbv2.model.UpdateItemRequest;
import com.amazonaws.services.dynamodbv2.model.UpdateItemResult;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import no.unit.nva.identifiers.SortableIdentifier;
import no.unit.nva.model.Publication;
import no.unit.nva.model.PublicationStatus;
import no.unit.nva.publication.exception.DynamoDBException;
import no.unit.nva.publication.model.business.DoiRequest;
import no.unit.nva.publication.model.business.Resource;
import no.unit.nva.publication.model.business.TicketStatus;
import no.unit.nva.publication.model.business.UserInstance;
import no.unit.nva.publication.model.storage.DoiRequestDao;
import no.unit.nva.publication.model.storage.WithByTypeCustomerStatusIndex;
import no.unit.nva.publication.storage.model.DatabaseConstants;
import nva.commons.apigateway.exceptions.ApiGatewayException;
import nva.commons.apigateway.exceptions.BadRequestException;
import nva.commons.apigateway.exceptions.NotFoundException;
import nva.commons.core.SingletonCollector;
import nva.commons.core.attempt.Failure;
import nva.commons.core.attempt.Try;

public class DoiRequestService extends ServiceWithTransactions {
    
    public static final String DOI_REQUEST_WAS_NOT_FOUND_ERROR = "DoiRequest was not found";
    public static final String DOI_REQUEST_NOT_FOUND_FOR_RESOURCE = "Could not find a DOI Request for Resource: ";
    public static final String UPDATE_DOI_REQUEST_STATUS_CONDITION_FAILURE_MESSAGE =
        "Could not update DOI request status. Updating DOI request status for "
        + "unpublished resources is not possible";
    private static final Supplier<SortableIdentifier> DEFAULT_IDENTIFIER_PROVIDER = SortableIdentifier::next;
    private static final int DEFAULT_QUERY_RESULT_SIZE = 10_000;
    private final AmazonDynamoDB client;
    private final Clock clock;
    private final ResourceService resourceService;
    private final Supplier<SortableIdentifier> identifierProvider;
    
    public DoiRequestService(AmazonDynamoDB client, Clock clock) {
        this(client,
            clock,
            DEFAULT_IDENTIFIER_PROVIDER);
    }
    
    protected DoiRequestService(AmazonDynamoDB client,
                                Clock clock,
                                Supplier<SortableIdentifier> identifierProvider) {
        super();
        this.client = client;
        this.clock = clock;
        this.resourceService = new ResourceService(client, clock);
        this.identifierProvider = identifierProvider;
    }
    
    public static DoiRequest getDoiRequestByResourceIdentifier(UserInstance resourceOwner,
                                                               SortableIdentifier resourceIdentifier,
                                                               String tableName,
                                                               AmazonDynamoDB client
    ) throws NotFoundException {
        var queryObject = DoiRequestDao.queryByCustomerAndResourceIdentifier(resourceOwner,
            resourceIdentifier);
        QueryRequest queryRequest = new QueryRequest()
            .withTableName(tableName)
            .withIndexName(BY_CUSTOMER_RESOURCE_INDEX_NAME)
            .withKeyConditions(queryObject.byResource(queryObject.joinByResourceContainedOrderedType()));
        QueryResult queryResult = client.query(queryRequest);
        
        Map<String, AttributeValue> item = parseQueryResultExpectingSingleItem(queryResult)
            .orElseThrow(
                fail -> handleFetchDoiRequestByResoureError(resourceIdentifier));
        DoiRequestDao dao = parseAttributeValuesMap(item, DoiRequestDao.class);
        return dao.getData();
    }
    
    public DoiRequest getDoiRequestByResourceIdentifier(UserInstance resourceOwner,
                                                        SortableIdentifier resourceIdentifier)
        throws NotFoundException {
        return getDoiRequestByResourceIdentifier(resourceOwner, resourceIdentifier, RESOURCES_TABLE_NAME, client);
    }
    
    public SortableIdentifier createDoiRequest(UserInstance userInstance, SortableIdentifier resourceIdentifier)
        throws BadRequestException {
        Publication publication = fetchPublication(userInstance, resourceIdentifier);
        return createDoiRequest(publication);
    }
    
    public SortableIdentifier createDoiRequest(Publication publication) {
        var doiRequest = createNewDoiRequestEntry(publication).toDao();
        var request = doiRequest.createInsertionTransactionRequest();
        sendTransactionWriteRequest(request);
        return doiRequest.getIdentifier();
    }
    
    public List<DoiRequest> listDoiRequestsForPublishedPublications(UserInstance userInstance) {
        QueryRequest query = queryForListingDoiRequestsForPublishedResourcesByCustomer(userInstance);
        
        QueryResult result = client.query(query);
        
        return parseListingDoiRequestsQueryResult(result);
    }
    
    public DoiRequest getDoiRequest(UserInstance userInstance, SortableIdentifier doiRequestIdentifier)
        throws NotFoundException {
        
        DoiRequestDao queryObject = queryObject(userInstance.getOrganizationUri(),
            userInstance.getUserIdentifier(), doiRequestIdentifier);
        GetItemRequest getItemRequest = new GetItemRequest()
            .withTableName(RESOURCES_TABLE_NAME)
            .withKey(queryObject.primaryKey());
        Map<String, AttributeValue> item = executeGetRequest(getItemRequest);
        DoiRequestDao dao = parseAttributeValuesMap(item, DoiRequestDao.class);
        return dao.getData();
    }
    
    public DoiRequest updateDoiRequest(UserInstance userInstance,
                                       SortableIdentifier resourceIdentifier,
                                       TicketStatus status) throws ApiGatewayException {
        
        UpdateItemRequest updateItemRequest =
            createRequestForUpdatingDoiRequest(userInstance, resourceIdentifier, status);
        
        UpdateItemResult item = attempt(() -> client.updateItem(updateItemRequest))
            .orElseThrow(this::handleUpdateDoiRequestFailure);
        DoiRequestDao updatedEntry = parseAttributeValuesMap(item.getAttributes(), DoiRequestDao.class);
        return updatedEntry.getData();
    }
    
    public List<DoiRequest> listDoiRequestsForUser(UserInstance userInstance) {
        return listDoiRequestsForUser(userInstance, DEFAULT_QUERY_RESULT_SIZE);
    }
    
    protected List<DoiRequest> listDoiRequestsForUser(UserInstance userInstance, int maxResultSize) {
        QueryRequest query = listDoiRequestForUserQuery(userInstance, maxResultSize);
        return performQueryWithPotentiallyManyResults(query);
    }
    
    @Override
    protected AmazonDynamoDB getClient() {
        return client;
    }
    
    @Override
    protected Clock getClock() {
        return clock;
    }
    
    private static NotFoundException handleFetchDoiRequestByResoureError(SortableIdentifier resourceIdentifier) {
        return new NotFoundException(DOI_REQUEST_NOT_FOUND_FOR_RESOURCE + resourceIdentifier.toString());
    }
    
    private static Try<Map<String, AttributeValue>> parseQueryResultExpectingSingleItem(QueryResult queryResult) {
        return attempt(() -> queryResult.getItems()
            .stream()
            .collect(SingletonCollector.collect()));
    }
    
    private ApiGatewayException handleUpdateDoiRequestFailure(Failure<UpdateItemResult> fail) {
        if (updateConditionFailed(fail.getException())) {
            return new BadRequestException(UPDATE_DOI_REQUEST_STATUS_CONDITION_FAILURE_MESSAGE);
        }
        return new DynamoDBException(fail.getException());
    }
    
    private boolean updateConditionFailed(Exception error) {
        return error instanceof ConditionalCheckFailedException;
    }
    
    private UpdateItemRequest createRequestForUpdatingDoiRequest(UserInstance userInstance,
                                                                 SortableIdentifier resourceIdentifier,
                                                                 TicketStatus status) throws NotFoundException {
        String now = nowAsString();
        
        DoiRequestDao dao = createUpdatedDoiRequestDao(userInstance, resourceIdentifier, status);
        String updateExpression = "SET"
                                  + "#data.#status = :status, "
                                  + "#data.#modifiedDate = :modifiedDate,"
                                  + "#PK1 = :PK1 ,"
                                  + "#SK1 = :SK1 ,"
                                  + "#PK2 = :PK2 ,"
                                  + "#SK2 = :SK2 ";
        
        String conditionExpression = "#data.#resourceStatus = :publishedStatus";
        
        Map<String, String> expressionAttributeNames = Map.of(
            "#data", CONTAINED_DATA_FIELD_NAME,
            "#status", STATUS_FIELD,
            "#modifiedDate", MODIFIED_DATE_FIELD,
            "#resourceStatus", RESOURCE_STATUS_FIELD,
            "#PK1", BY_TYPE_CUSTOMER_STATUS_INDEX_PARTITION_KEY_NAME,
            "#SK1", BY_TYPE_CUSTOMER_STATUS_INDEX_SORT_KEY_NAME,
            "#PK2", BY_CUSTOMER_RESOURCE_INDEX_PARTITION_KEY_NAME,
            "#SK2", BY_CUSTOMER_RESOURCE_INDEX_SORT_KEY_NAME
        
        );
        
        Map<String, AttributeValue> expressionAttributeValues = Map.of(
            ":status", new AttributeValue(dao.getData().getStatus().name()),
            ":modifiedDate", new AttributeValue(now),
            ":publishedStatus", new AttributeValue(PublicationStatus.PUBLISHED.toString()),
            ":PK1", new AttributeValue(dao.getByTypeCustomerStatusPartitionKey()),
            ":SK1", new AttributeValue(dao.getByTypeCustomerStatusSortKey()),
            ":PK2", new AttributeValue(dao.getByCustomerAndResourcePartitionKey()),
            ":SK2", new AttributeValue(dao.getByCustomerAndResourceSortKey())
        );
        return new UpdateItemRequest()
            .withTableName(RESOURCES_TABLE_NAME)
            .withKey(dao.primaryKey())
            .withUpdateExpression(updateExpression)
            .withConditionExpression(conditionExpression)
            .withExpressionAttributeNames(expressionAttributeNames)
            .withExpressionAttributeValues(expressionAttributeValues)
            .withReturnValues(ReturnValue.ALL_NEW);
    }
    
    private DoiRequestDao createUpdatedDoiRequestDao(UserInstance userInstance, SortableIdentifier resourceIdentifier,
                                                     TicketStatus status) throws NotFoundException {
        DoiRequest doiRequest = getDoiRequestByResourceIdentifier(userInstance, resourceIdentifier);
        TicketStatus existingStatus = doiRequest.getStatus();
        doiRequest.setStatus(existingStatus.changeStatus(status));
        return new DoiRequestDao(doiRequest);
    }
    
    private Map<String, AttributeValue> executeGetRequest(GetItemRequest getItemRequest)
        throws NotFoundException {
        GetItemResult result = client.getItem(getItemRequest);
        Map<String, AttributeValue> item = result.getItem();
        if (isNull(item) || item.isEmpty()) {
            throw new NotFoundException(DOI_REQUEST_WAS_NOT_FOUND_ERROR);
        }
        return item;
    }
    
    private List<DoiRequest> performQueryWithPotentiallyManyResults(QueryRequest query) {
        Map<String, AttributeValue> startKey = null;
        List<DoiRequest> result = new ArrayList<>();
        do {
            query.withExclusiveStartKey(startKey);
            QueryResult queryResult = client.query(query);
            List<DoiRequest> lastItems = parseListingDoiRequestsQueryResult(queryResult);
            result.addAll(lastItems);
            startKey = queryResult.getLastEvaluatedKey();
        } while (noMoreResults(startKey));
        return result;
    }
    
    private boolean noMoreResults(Map<String, AttributeValue> startKey) {
        return startKey != null && !startKey.isEmpty();
    }
    
    private QueryRequest listDoiRequestForUserQuery(UserInstance userInstance, int maxResultSize) {
        String queryExpression = "#PK= :PK";
        String filterExpression = "#data.#status = :requestedStatus OR #data.#status = :rejectedStatus";
        
        Map<String, String> expressionAttributeNames =
            Map.of(
                "#PK", DatabaseConstants.PRIMARY_KEY_PARTITION_KEY_NAME,
                "#data", CONTAINED_DATA_FIELD_NAME,
                "#status", STATUS_FIELD);
        
        String primaryKeyPartitionKeyValue = doiRequestPrimaryKeyPartionKeyValue(userInstance);
        
        Map<String, AttributeValue> expressionAttributeValues = Map.of(
            ":PK", new AttributeValue(primaryKeyPartitionKeyValue),
            ":requestedStatus", new AttributeValue(TicketStatus.PENDING.toString()),
            ":rejectedStatus", new AttributeValue(TicketStatus.CLOSED.toString())
        );
        
        return new QueryRequest()
            .withTableName(RESOURCES_TABLE_NAME)
            .withKeyConditionExpression(queryExpression)
            .withFilterExpression(filterExpression)
            .withExpressionAttributeNames(expressionAttributeNames)
            .withExpressionAttributeValues(expressionAttributeValues)
            .withLimit(maxResultSize);
    }
    
    private String doiRequestPrimaryKeyPartionKeyValue(UserInstance userInstance) {
        DoiRequestDao queryObject = queryObject(userInstance.getOrganizationUri(), userInstance.getUserIdentifier());
        return queryObject.getPrimaryKeyPartitionKey();
    }
    
    private QueryRequest queryForListingDoiRequestsForPublishedResourcesByCustomer(UserInstance userInstance) {
        final String keyConditionExpression = "#partitionKeyName = :partitionKeyValue";
        final String partitionKeyValue = formatPartitionKeyValueForByTypeCustomerStatusIndex(userInstance);
        final String filterExpression = "#data.#resourceStatus = :publishedStatus";
        
        Map<String, String> expressionAttributeNames = Map.of(
            "#data", CONTAINED_DATA_FIELD_NAME,
            "#resourceStatus", RESOURCE_STATUS_FIELD,
            "#partitionKeyName", BY_TYPE_CUSTOMER_STATUS_INDEX_PARTITION_KEY_NAME
        );
        
        Map<String, AttributeValue> expressionAttributeValues = Map.of(
            ":partitionKeyValue", new AttributeValue(partitionKeyValue),
            ":publishedStatus", new AttributeValue(PublicationStatus.PUBLISHED.toString())
        );
        
        return new QueryRequest()
            .withTableName(RESOURCES_TABLE_NAME)
            .withIndexName(BY_TYPE_CUSTOMER_STATUS_INDEX_NAME)
            .withKeyConditionExpression(keyConditionExpression)
            .withFilterExpression(filterExpression)
            .withExpressionAttributeNames(expressionAttributeNames)
            .withExpressionAttributeValues(expressionAttributeValues);
    }
    
    private String formatPartitionKeyValueForByTypeCustomerStatusIndex(UserInstance userInstance) {
        return WithByTypeCustomerStatusIndex.formatByTypeCustomerStatusPartitionKey(
            DoiRequestDao.getContainedType(),
            TicketStatus.PENDING.toString(),
            userInstance.getOrganizationUri()
        );
    }
    
    private List<DoiRequest> parseListingDoiRequestsQueryResult(QueryResult result) {
        return result.getItems()
            .stream()
            .map(map -> parseAttributeValuesMap(map, DoiRequestDao.class))
            .map(DoiRequestDao::getData)
            .collect(Collectors.toList());
    }
    
    private Publication fetchPublication(UserInstance userInstance, SortableIdentifier resourceIdentifier)
        throws BadRequestException {
        return attempt(() -> resourceService.getPublication(userInstance, resourceIdentifier))
            .orElseThrow(this::handleResourceNotFetchedError);
    }
    
    private BadRequestException handleResourceNotFetchedError(Failure<Publication> fail) {
        return new BadRequestException(fail.getException().getMessage(),fail.getException());
    }
    
    private DoiRequest createNewDoiRequestEntry(Publication publication) {
        Resource resource = Resource.fromPublication(publication);
        return DoiRequest.newDoiRequestForResource(identifierProvider.get(), resource, clock.instant());
    }
    
}
