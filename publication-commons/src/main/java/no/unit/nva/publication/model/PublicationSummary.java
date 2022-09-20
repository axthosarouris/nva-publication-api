package no.unit.nva.publication.model;

import static java.util.Objects.nonNull;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import java.net.URI;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import no.unit.nva.identifiers.SortableIdentifier;
import no.unit.nva.model.Contributor;
import no.unit.nva.model.Publication;
import no.unit.nva.model.PublicationDate;
import no.unit.nva.model.PublicationStatus;
import no.unit.nva.model.instancetypes.PublicationInstance;
import no.unit.nva.model.pages.Pages;
import no.unit.nva.publication.PublicationServiceConfig;
import no.unit.nva.publication.model.business.User;
import nva.commons.core.JacocoGenerated;
import nva.commons.core.paths.UriWrapper;

@JsonTypeName(PublicationSummary.TYPE)
public class PublicationSummary {
    
    public static final String TYPE = "PublicationSummary";
    
    @JsonProperty("id")
    private URI publicationId;
    @JsonProperty("mainTitle")
    private String title;
    @JsonProperty
    private User owner;
    @JsonProperty
    private Instant createdDate;
    @JsonProperty
    private Instant modifiedDate;
    @JsonProperty
    private PublicationInstance<? extends Pages> publicationInstance;
    @JsonProperty
    private PublicationDate publicationDate;
    @JsonProperty
    private String publicationYear;
    @JsonProperty
    private List<Contributor> contributors;
    @JsonProperty
    private PublicationStatus status;
    
    public static PublicationSummary create(Publication publication) {
        var publicationSummary = new PublicationSummary();
        publicationSummary.setPublicationId(toPublicationId(publication.getIdentifier()));
        publicationSummary.setCreatedDate(publication.getCreatedDate());
        publicationSummary.setModifiedDate(publication.getModifiedDate());
        publicationSummary.setOwner(new User(publication.getResourceOwner().getOwner()));
        publicationSummary.setStatus(publication.getStatus());
        if (nonNull(publication.getEntityDescription())) {
            publicationSummary.setContributors(publication.getEntityDescription().getContributors());
            publicationSummary.setPublicationDate(publication.getEntityDescription().getDate());
            publicationSummary.setTitle(publication.getEntityDescription().getMainTitle());
            if (nonNull(publication.getEntityDescription().getDate())) {
                publicationSummary.setPublicationYear(publication.getEntityDescription().getDate().getYear());
            }
            if (nonNull(publication.getEntityDescription().getReference())) {
                publicationSummary.setPublicationInstance(
                    publication.getEntityDescription().getReference().getPublicationInstance());
            }
        }
        return publicationSummary;
    }
    
    public static PublicationSummary create(URI publicationId, String publicationTitle) {
        var publicationSummary = new PublicationSummary();
        publicationSummary.setPublicationId(publicationId);
        publicationSummary.setTitle(publicationTitle);
        return publicationSummary;
    }
    
    public PublicationStatus getStatus() {
        return status;
    }
    
    public void setStatus(PublicationStatus status) {
        this.status = status;
    }
    
    public URI getPublicationId() {
        return publicationId;
    }
    
    public void setPublicationId(URI publicationId) {
        this.publicationId = publicationId;
    }
    
    public SortableIdentifier extractPublicationIdentifier() {
        return new SortableIdentifier(UriWrapper.fromUri(publicationId).getLastPathElement());
    }
    
    public String getTitle() {
        return title;
    }
    
    public void setTitle(String title) {
        this.title = title;
    }
    
    public Instant getModifiedDate() {
        return modifiedDate;
    }
    
    public void setModifiedDate(Instant modifiedDate) {
        this.modifiedDate = modifiedDate;
    }
    
    public PublicationInstance<? extends Pages> getPublicationInstance() {
        return publicationInstance;
    }
    
    public void setPublicationInstance(
        PublicationInstance<? extends Pages> publicationInstance) {
        this.publicationInstance = publicationInstance;
    }
    
    public PublicationDate getPublicationDate() {
        return publicationDate;
    }
    
    public void setPublicationDate(PublicationDate publicationDate) {
        this.publicationDate = publicationDate;
    }
    
    public String getPublicationYear() {
        return publicationYear;
    }
    
    public void setPublicationYear(String publicationYear) {
        this.publicationYear = publicationYear;
    }
    
    public List<Contributor> getContributors() {
        return nonNull(contributors) ? contributors : Collections.emptyList();
    }
    
    public void setContributors(List<Contributor> contributors) {
        this.contributors = contributors;
    }
    
    @Override
    @JacocoGenerated
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PublicationSummary)) {
            return false;
        }
        PublicationSummary that = (PublicationSummary) o;
        return Objects.equals(getPublicationId(), that.getPublicationId())
               && Objects.equals(getTitle(), that.getTitle())
               && Objects.equals(getOwner(), that.getOwner())
               && Objects.equals(getCreatedDate(), that.getCreatedDate())
               && Objects.equals(getModifiedDate(), that.getModifiedDate())
               && Objects.equals(getPublicationInstance(), that.getPublicationInstance())
               && Objects.equals(getPublicationDate(), that.getPublicationDate())
               && Objects.equals(getPublicationYear(), that.getPublicationYear())
               && Objects.equals(getContributors(), that.getContributors())
               && getStatus() == that.getStatus();
    }
    
    @Override
    @JacocoGenerated
    public int hashCode() {
        return Objects.hash(getPublicationId(), getTitle(), getOwner(), getCreatedDate(), getModifiedDate(),
            getPublicationInstance(), getPublicationDate(), getPublicationYear(), getContributors(), getStatus());
    }
    
    public Instant getCreatedDate() {
        return createdDate;
    }
    
    public void setCreatedDate(Instant createdDate) {
        this.createdDate = createdDate;
    }
    
    public User getOwner() {
        return owner;
    }
    
    public void setOwner(User owner) {
        this.owner = owner;
    }
    
    private static URI toPublicationId(SortableIdentifier identifier) {
        return UriWrapper.fromUri(PublicationServiceConfig.PUBLICATION_HOST_URI)
                   .addChild(identifier.toString()).getUri();
    }
}
