package no.unit.nva.publication.model.storage;

import static no.unit.nva.publication.storage.model.DatabaseConstants.BY_CUSTOMER_RESOURCE_INDEX_NAME;
import static no.unit.nva.publication.storage.model.DatabaseConstants.KEY_FIELDS_DELIMITER;
import static no.unit.nva.publication.storage.model.DatabaseConstants.RESOURCES_TABLE_NAME;
import com.amazonaws.services.dynamodbv2.model.QueryRequest;
import com.amazonaws.services.dynamodbv2.model.TransactWriteItem;
import com.amazonaws.services.dynamodbv2.model.TransactWriteItemsRequest;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import java.net.URI;
import java.util.Objects;
import no.unit.nva.commons.json.JsonSerializable;
import no.unit.nva.identifiers.SortableIdentifier;
import no.unit.nva.publication.model.business.PublishingRequestCase;
import no.unit.nva.publication.model.business.TicketEntry;
import no.unit.nva.publication.model.business.User;
import no.unit.nva.publication.model.business.UserInstance;
import nva.commons.core.JacocoGenerated;

@JsonTypeName(PublishingRequestDao.TYPE)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
public class PublishingRequestDao extends TicketDao implements JoinWithResource, JsonSerializable {
    
    public static final String BY_RESOURCE_INDEX_ORDER_PREFIX = "c";
    public static final String TYPE = "PublishingRequestCase";
    
    @JacocoGenerated
    public PublishingRequestDao() {
        super();
    }
    
    public PublishingRequestDao(TicketEntry data) {
        super(data);
    }
    
    public static QueryRequest queryPublishingRequestByResource(URI customerId,
                                                                SortableIdentifier resourceIdentifier) {
        var queryObject = PublishingRequestCase.createQueryObject(resourceIdentifier, customerId);
        var dao = new PublishingRequestDao(queryObject);
        
        return new QueryRequest()
                   .withTableName(RESOURCES_TABLE_NAME)
                   .withIndexName(BY_CUSTOMER_RESOURCE_INDEX_NAME)
                   .withKeyConditions(dao.byResource(dao.joinByResourceOrderedType()));
    }
    
    public static PublishingRequestDao queryObject(PublishingRequestCase queryObject) {
        return new PublishingRequestDao(queryObject);
    }
    
    public static PublishingRequestDao queryByCustomerAndResourceIdentifier(UserInstance resourceOwner,
                                                                            SortableIdentifier resourceIdentifier) {
        var queryObject =
            PublishingRequestCase.createQueryObject(resourceOwner, resourceIdentifier, null);
        return new PublishingRequestDao(queryObject);
    }
    
    @JsonIgnore
    public static String joinByResourceContainedOrderedType() {
        return BY_RESOURCE_INDEX_ORDER_PREFIX + KEY_FIELDS_DELIMITER + PublishingRequestCase.TYPE;
    }
    
    @Override
    public URI getCustomerId() {
        return getData().getCustomerId();
    }
    
    @Override
    public TransactWriteItemsRequest createInsertionTransactionRequest() {
        var publishingRequestInsertionEntry = createPublishingRequestInsertionEntry();
        var identifierEntry = createUniqueIdentifierEntry();
        var publishingRequestUniquenessEntry = createPublishingRequestUniquenessEntry();
        return new TransactWriteItemsRequest()
                   .withTransactItems(
                       identifierEntry,
                       publishingRequestInsertionEntry,
                       publishingRequestUniquenessEntry);
    }
    
    @Override
    @JacocoGenerated
    public int hashCode() {
        return Objects.hash(getData());
    }
    
    @Override
    @JacocoGenerated
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PublishingRequestDao)) {
            return false;
        }
        PublishingRequestDao that = (PublishingRequestDao) o;
        return Objects.equals(getData(), that.getData());
    }
    
    @Override
    protected User getOwner() {
        return getData().getOwner();
    }
    
    @Override
    public String joinByResourceOrderedType() {
        return joinByResourceContainedOrderedType();
    }
    
    @Override
    public SortableIdentifier getResourceIdentifier() {
        return getTicketEntry().extractPublicationIdentifier();
    }
    
    @Override
    @JacocoGenerated
    public String toString() {
        return toJsonString();
    }
    
    private TransactWriteItem createPublishingRequestUniquenessEntry() {
        var publishingRequestUniquenessEntry =
            UniquePublishingRequestEntry.create((PublishingRequestCase) getData());
        return newPutTransactionItem(publishingRequestUniquenessEntry);
    }
    
    private TransactWriteItem createPublishingRequestInsertionEntry() {
        var dynamoEntry = new PublishingRequestDao(getTicketEntry());
        return newPutTransactionItem(dynamoEntry);
    }
    
    private TransactWriteItem createUniqueIdentifierEntry() {
        var identifierEntry = new IdentifierEntry(getData().getIdentifier().toString());
        return newPutTransactionItem(identifierEntry);
    }
}

