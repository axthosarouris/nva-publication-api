package no.unit.nva.publication.model.business;

import static no.unit.nva.publication.model.business.TicketEntry.Constants.CREATED_DATE_FIELD;
import static no.unit.nva.publication.model.business.TicketEntry.Constants.CUSTOMER_ID_FIELD;
import static no.unit.nva.publication.model.business.TicketEntry.Constants.IDENTIFIER_FIELD;
import static no.unit.nva.publication.model.business.TicketEntry.Constants.MODIFIED_DATE_FIELD;
import static no.unit.nva.publication.model.business.TicketEntry.Constants.OWNER_FIELD;
import static no.unit.nva.publication.model.business.TicketEntry.Constants.RESOURCE_IDENTIFIER_FIELD;
import static no.unit.nva.publication.model.business.TicketEntry.Constants.STATUS_FIELD;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import java.net.URI;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import no.unit.nva.identifiers.SortableIdentifier;
import no.unit.nva.model.Organization;
import no.unit.nva.model.Publication;
import no.unit.nva.model.ResourceOwner;
import no.unit.nva.publication.model.storage.Dao;
import no.unit.nva.publication.model.storage.GeneralSupportRequestDao;
import nva.commons.core.JacocoGenerated;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonTypeName(GeneralSupportRequest.TYPE)
public class GeneralSupportRequest implements TicketEntry {
    
    public static final String TYPE = "GeneralSupportRequest";
    @JsonProperty(IDENTIFIER_FIELD)
    private SortableIdentifier identifier;
    @JsonProperty(VERSION_FIELD)
    private UUID version;
    @JsonProperty(CREATED_DATE_FIELD)
    private Instant createdDate;
    @JsonProperty(MODIFIED_DATE_FIELD)
    private Instant modifiedDate;
    @JsonProperty(OWNER_FIELD)
    private String owner;
    @JsonProperty(CUSTOMER_ID_FIELD)
    private URI customerId;
    @JsonProperty(RESOURCE_IDENTIFIER_FIELD)
    private SortableIdentifier resourceIdentifier;
    @JsonProperty(STATUS_FIELD)
    private TicketStatus status;
    
    public static TicketEntry fromPublication(Publication publication) {
        var ticket = new GeneralSupportRequest();
        ticket.setResourceIdentifier(publication.getIdentifier());
        ticket.setOwner(extractOwner(publication));
        ticket.setCustomerId(extractCustomerId(publication));
        ticket.setCreatedDate(Instant.now());
        ticket.setModifiedDate(Instant.now());
        ticket.setStatus(TicketStatus.PENDING);
        ticket.setIdentifier(SortableIdentifier.next());
        ticket.setVersion(UUID.randomUUID());
        return ticket;
    }
    
    public static GeneralSupportRequest createQueryObject(URI customerId, SortableIdentifier resourceIdentifier) {
        var ticket = new GeneralSupportRequest();
        ticket.setCustomerId(customerId);
        ticket.setResourceIdentifier(resourceIdentifier);
        return ticket;
    }
    
    @Override
    public SortableIdentifier getIdentifier() {
        return identifier;
    }
    
    @Override
    public void setIdentifier(SortableIdentifier identifier) {
        this.identifier = identifier;
    }
    
    @JacocoGenerated
    @Override
    public Publication toPublication() {
        throw new UnsupportedOperationException();
    }
    
    @Override
    public UUID getVersion() {
        return this.version;
    }
    
    @Override
    public void setVersion(UUID version) {
        this.version = version;
    }
    
    @Override
    public String getType() {
        return TYPE;
    }
    
    @Override
    public Instant getCreatedDate() {
        return this.createdDate;
    }
    
    @Override
    public void setCreatedDate(Instant createdDate) {
        this.createdDate = createdDate;
    }
    
    @Override
    public Instant getModifiedDate() {
        return this.modifiedDate;
    }
    
    @Override
    public void setModifiedDate(Instant modifiedDate) {
        this.modifiedDate = modifiedDate;
    }
    
    @Override
    public String getOwner() {
        return this.owner;
    }
    
    public void setOwner(String owner) {
        this.owner = owner;
    }
    
    @Override
    public URI getCustomerId() {
        return this.customerId;
    }
    
    public void setCustomerId(URI customerId) {
        this.customerId = customerId;
    }
    
    @Override
    public Dao toDao() {
        return new GeneralSupportRequestDao(this);
    }
    
    @Override
    public String getStatusString() {
        return this.getStatus().toString();
    }
    
    @Override
    public SortableIdentifier getResourceIdentifier() {
        return this.resourceIdentifier;
    }
    
    public void setResourceIdentifier(SortableIdentifier resourceIdentifier) {
        this.resourceIdentifier = resourceIdentifier;
    }
    
    @Override
    public void validateCreationRequirements(Publication publication) {
        //NO OP
    }
    
    @Override
    public void validateCompletionRequirements(Publication publication) {
        //NO OP
    }
    
    @Override
    public TicketEntry copy() {
        var copy = new GeneralSupportRequest();
        copy.setStatus(this.getStatus());
        copy.setVersion(this.getVersion());
        copy.setModifiedDate(this.getModifiedDate());
        copy.setIdentifier(this.getIdentifier());
        copy.setType(this.getType());
        copy.setCreatedDate(this.getCreatedDate());
        copy.setCustomerId(this.getCustomerId());
        copy.setOwner(this.getOwner());
        copy.setResourceIdentifier(this.getResourceIdentifier());
        return copy;
    }
    
    @Override
    public TicketStatus getStatus() {
        return this.status;
    }
    
    @Override
    public void setStatus(TicketStatus ticketStatus) {
        this.status = ticketStatus;
    }
    
    @Override
    @JacocoGenerated
    public int hashCode() {
        return Objects.hash(getIdentifier(), getVersion(), getCreatedDate(), getModifiedDate(), getOwner(),
            getCustomerId(),
            getResourceIdentifier(), getStatus());
    }
    
    @Override
    @JacocoGenerated
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof GeneralSupportRequest)) {
            return false;
        }
        GeneralSupportRequest that = (GeneralSupportRequest) o;
        return Objects.equals(getIdentifier(), that.getIdentifier())
               && Objects.equals(getVersion(), that.getVersion())
               && Objects.equals(getCreatedDate(), that.getCreatedDate())
               && Objects.equals(getModifiedDate(), that.getModifiedDate())
               && Objects.equals(getOwner(), that.getOwner())
               && Objects.equals(getCustomerId(), that.getCustomerId())
               && Objects.equals(getResourceIdentifier(), that.getResourceIdentifier())
               && getStatus() == that.getStatus();
    }
    
    private static URI extractCustomerId(Publication publication) {
        return Optional.of(publication).map(Publication::getPublisher).map(Organization::getId).orElse(null);
    }
    
    private static String extractOwner(Publication publication) {
        return Optional.of(publication).map(Publication::getResourceOwner).map(ResourceOwner::getOwner).orElse(null);
    }
}
