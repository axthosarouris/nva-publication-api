package no.unit.nva.publication.service.impl;

import static no.unit.nva.publication.PublicationServiceConfig.dtoObjectMapper;
import static no.unit.nva.publication.model.storage.Dao.CONTAINED_DATA_FIELD_NAME;
import static no.unit.nva.publication.service.impl.ReadResourceService.RESOURCE_NOT_FOUND_MESSAGE;
import static no.unit.nva.publication.service.impl.ResourceService.AWAIT_TIME_BEFORE_FETCH_RETRY;
import static no.unit.nva.publication.service.impl.ResourceServiceUtils.KEY_NOT_EXISTS_CONDITION;
import static no.unit.nva.publication.service.impl.ResourceServiceUtils.PRIMARY_KEY_EQUALITY_CONDITION_ATTRIBUTE_NAMES;
import static no.unit.nva.publication.storage.model.DatabaseConstants.RESOURCES_TABLE_NAME;
import static nva.commons.core.attempt.Try.attempt;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.model.Delete;
import com.amazonaws.services.dynamodbv2.model.Put;
import com.amazonaws.services.dynamodbv2.model.TransactWriteItem;
import com.amazonaws.services.dynamodbv2.model.TransactWriteItemsRequest;
import com.amazonaws.services.dynamodbv2.model.TransactWriteItemsResult;
import java.time.Clock;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import no.unit.nva.publication.exception.BadRequestException;
import no.unit.nva.publication.exception.TransactionFailedException;
import no.unit.nva.publication.model.business.Entity;
import no.unit.nva.publication.model.storage.Dao;
import no.unit.nva.publication.model.storage.DoiRequestDao;
import no.unit.nva.publication.model.storage.DynamoEntry;
import no.unit.nva.publication.model.storage.ResourceDao;
import no.unit.nva.publication.model.storage.WithPrimaryKey;
import nva.commons.core.attempt.Failure;
import nva.commons.core.attempt.FunctionWithException;

public abstract class ServiceWithTransactions {
    
    public static final String EMPTY_STRING = "";
    public static final String DOUBLE_QUOTES = "\"";
    public static final String RAWTYPES = "rawtypes";
    public static final String RESOURCE_FIELD_IN_RESOURCE_DAO = CONTAINED_DATA_FIELD_NAME;
    public static final String STATUS_FIELD_IN_RESOURCE = "status";
    public static final String MODIFIED_FIELD_IN_RESOURCE = "modifiedDate";
    public static final String RESOURCE_FILE_SET_FIELD = "fileSet";
    public static final int DOI_REQUEST_INDEX_IN_QUERY_RESULT_WHEN_DOI_REQUEST_EXISTS = 0;
    private static final Integer MAX_FETCH_ATTEMPTS = 3;
    private static final int RESOURCE_INDEX_IN_QUERY_RESULT_WHEN_DOI_REQUEST_EXISTS = 1;
    private static final int RESOURCE_INDEX_IN_QUERY_RESULT_WHEN_DOI_REQUEST_NOT_EXISTS = 0;
    
    protected static <T extends DynamoEntry> TransactWriteItem newPutTransactionItem(T data) {
        
        Put put = new Put()
            .withItem(data.toDynamoFormat())
            .withTableName(RESOURCES_TABLE_NAME)
            .withConditionExpression(KEY_NOT_EXISTS_CONDITION)
            .withExpressionAttributeNames(PRIMARY_KEY_EQUALITY_CONDITION_ATTRIBUTE_NAMES);
        return new TransactWriteItem().withPut(put);
    }
    
    protected static TransactWriteItemsRequest newTransactWriteItemsRequest(TransactWriteItem... transaction) {
        return newTransactWriteItemsRequest(Arrays.asList(transaction));
    }
    
    protected static TransactWriteItemsRequest newTransactWriteItemsRequest(List<TransactWriteItem> transactionItems) {
        return new TransactWriteItemsRequest().withTransactItems(transactionItems);
    }
    
    protected <T extends Entity, E extends Exception> Optional<T> fetchEventualConsistentDataEntry(
        T dynamoEntry,
        FunctionWithException<T, T, E> nonEventuallyConsistentFetch) {
        T savedEntry = null;
        for (int times = 0; times < MAX_FETCH_ATTEMPTS && savedEntry == null; times++) {
            savedEntry = attempt(() -> nonEventuallyConsistentFetch.apply(dynamoEntry)).orElse(fail -> null);
            attempt(this::waitBeforeFetching).orElseThrow();
        }
        return Optional.ofNullable(savedEntry);
    }
    
    protected abstract AmazonDynamoDB getClient();
    
    protected <T extends WithPrimaryKey> TransactWriteItem newDeleteTransactionItem(T dynamoEntry) {
        return new TransactWriteItem()
            .withDelete(new Delete().withTableName(RESOURCES_TABLE_NAME).withKey(dynamoEntry.primaryKey()));
    }
    
    protected Optional<DoiRequestDao> extractDoiRequest(List<Dao> daos) {
        if (doiRequestExists(daos)) {
            return Optional.of((DoiRequestDao) daos.get(DOI_REQUEST_INDEX_IN_QUERY_RESULT_WHEN_DOI_REQUEST_EXISTS));
        }
        return Optional.empty();
    }
    
    protected ResourceDao extractResourceDao(List<Dao> daos) throws BadRequestException {
        if (doiRequestExists(daos)) {
            return (ResourceDao) daos.get(RESOURCE_INDEX_IN_QUERY_RESULT_WHEN_DOI_REQUEST_EXISTS);
        } else if (onlyResourceExists(daos)) {
            return (ResourceDao) daos.get(RESOURCE_INDEX_IN_QUERY_RESULT_WHEN_DOI_REQUEST_NOT_EXISTS);
        }
        throw new BadRequestException(RESOURCE_NOT_FOUND_MESSAGE);
    }
    
    protected String nowAsString() {
        String jsonString = attempt(() -> dtoObjectMapper.writeValueAsString(getClock().instant()))
            .orElseThrow();
        return jsonString.replace(DOUBLE_QUOTES, EMPTY_STRING);
    }
    
    protected void sendTransactionWriteRequest(TransactWriteItemsRequest transactWriteItemsRequest) {
        attempt(() -> getClient().transactWriteItems(transactWriteItemsRequest))
            .orElseThrow(this::handleTransactionFailure);
    }
    
    protected abstract Clock getClock();
    
    private Void waitBeforeFetching() throws InterruptedException {
        Thread.sleep(AWAIT_TIME_BEFORE_FETCH_RETRY);
        return null;
    }
    
    private TransactionFailedException handleTransactionFailure(Failure<TransactWriteItemsResult> fail) {
        return new TransactionFailedException(fail.getException());
    }
    
    private boolean onlyResourceExists(List<Dao> daos) {
        return daos.size() == 1;
    }
    
    private boolean doiRequestExists(List<Dao> daos) {
        return daos.size() == 2;
    }
}
