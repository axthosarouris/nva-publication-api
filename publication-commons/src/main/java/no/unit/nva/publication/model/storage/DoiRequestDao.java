package no.unit.nva.publication.model.storage;

import com.amazonaws.services.dynamodbv2.model.TransactWriteItem;
import com.amazonaws.services.dynamodbv2.model.TransactWriteItemsRequest;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import java.net.URI;
import java.util.Objects;
import no.unit.nva.commons.json.JsonSerializable;
import no.unit.nva.identifiers.SortableIdentifier;
import no.unit.nva.publication.model.business.DoiRequest;
import no.unit.nva.publication.model.business.PublicationDetails;
import no.unit.nva.publication.model.business.User;
import no.unit.nva.publication.model.business.UserInstance;
import no.unit.nva.publication.storage.model.DatabaseConstants;
import nva.commons.core.JacocoGenerated;

@JsonTypeName(DoiRequestDao.TYPE)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
public class DoiRequestDao extends TicketDao
    implements
    JoinWithResource,
    JsonSerializable {
    
    public static final String JOIN_BY_RESOURCE_INDEX_ORDER_PREFIX = "b";
    public static final String TYPE = "DoiRequest";
    
    @JacocoGenerated
    public DoiRequestDao() {
        super();
    }
    
    public DoiRequestDao(DoiRequest doiRequest) {
        super(doiRequest);
    }
    
    public static DoiRequestDao queryObject(URI publisherId, User owner, SortableIdentifier doiRequestIdentifier) {
        DoiRequest doi = DoiRequest.builder()
                             .withIdentifier(doiRequestIdentifier)
                             .withOwner(owner)
                             .withCustomerId(publisherId)
                             .build();
        
        return new DoiRequestDao(doi);
    }
    
    public static DoiRequestDao queryObject(ResourceDao queryObject) {
        var doiRequest = DoiRequest.builder()
                             .withPublicationDetails(PublicationDetails.create(queryObject.getResourceIdentifier()))
                             .build();
        return new DoiRequestDao(doiRequest);
    }
    
    public static DoiRequestDao queryObject(URI publisherId, User owner) {
        return queryObject(publisherId, owner, null);
    }
    
    public static DoiRequestDao queryByCustomerAndResourceIdentifier(UserInstance resourceOwner,
                                                                     SortableIdentifier resourceIdentifier) {
        DoiRequest doi = DoiRequest.builder()
                             .withPublicationDetails(PublicationDetails.create(resourceIdentifier))
                             .withOwner(resourceOwner.getUser())
                             .withCustomerId(resourceOwner.getOrganizationUri())
                             .build();
        return new DoiRequestDao(doi);
    }
    
    public String joinByResourceContainedOrderedType() {
        return JOIN_BY_RESOURCE_INDEX_ORDER_PREFIX + DatabaseConstants.KEY_FIELDS_DELIMITER + getData().getType();
    }
    
    @Override
    public URI getCustomerId() {
        return getData().getCustomerId();
    }
    
    @Override
    public TransactWriteItemsRequest createInsertionTransactionRequest() {
        TransactWriteItem doiRequestEntry = createDoiRequestInsertionEntry();
        TransactWriteItem identifierEntry = createUniqueIdentifierEntry();
        TransactWriteItem uniqueDoiRequestEntry = createUniqueDoiRequestEntry();
        
        return new TransactWriteItemsRequest()
                   .withTransactItems(
                       identifierEntry,
                       uniqueDoiRequestEntry,
                       doiRequestEntry);
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
        if (!(o instanceof DoiRequestDao)) {
            return false;
        }
        DoiRequestDao that = (DoiRequestDao) o;
        return Objects.equals(getData(), that.getData());
    }
    
    @Override
    protected User getOwner() {
        return getData().getOwner();
    }
    
    @Override
    @JsonIgnore
    public String joinByResourceOrderedType() {
        return joinByResourceContainedOrderedType();
    }
    
    @Override
    @JsonIgnore
    public SortableIdentifier getResourceIdentifier() {
        return getTicketEntry().extractPublicationIdentifier();
    }
    
    @Override
    @JacocoGenerated
    public String toString() {
        return toJsonString();
    }
    
    private TransactWriteItem createUniqueDoiRequestEntry() {
        UniqueDoiRequestEntry uniqueDoiRequestEntry = new UniqueDoiRequestEntry(
            getTicketEntry().extractPublicationIdentifier().toString());
        return newPutTransactionItem(uniqueDoiRequestEntry);
    }
    
    private TransactWriteItem createDoiRequestInsertionEntry() {
        return newPutTransactionItem(new DoiRequestDao((DoiRequest) getTicketEntry()));
    }
    
    private TransactWriteItem createUniqueIdentifierEntry() {
        IdentifierEntry identifierEntry = new IdentifierEntry(getData().getIdentifier().toString());
        return newPutTransactionItem(identifierEntry);
    }
}
