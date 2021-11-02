package no.unit.nva.expansion.model;

import no.unit.nva.expansion.ResourceExpansionServiceImpl;
import no.unit.nva.expansion.WithOrganizationScope;
import no.unit.nva.publication.storage.model.DoiRequest;

import java.net.URI;
import java.util.Set;

import static java.util.Collections.emptySet;
import static java.util.Objects.nonNull;

public final class ExpandedDoiRequest extends DoiRequest implements WithOrganizationScope, ExpandedResourceUpdate {

    private Set<URI> organizationIds;

    private ExpandedDoiRequest(DoiRequest doiRequest) {
        super();
        setDoi(doiRequest.getDoi());
        setContributors(doiRequest.getContributors());
        setCreatedDate(doiRequest.getCreatedDate());
        setIdentifier(doiRequest.getIdentifier());
        setCustomerId(doiRequest.getCustomerId());
        setModifiedDate(doiRequest.getModifiedDate());
        setOwner(doiRequest.getOwner());
        setResourceIdentifier(doiRequest.getResourceIdentifier());
        setResourceModifiedDate(doiRequest.getResourceModifiedDate());
        setResourcePublicationDate(doiRequest.getResourcePublicationDate());
        setResourcePublicationInstance(doiRequest.getResourcePublicationInstance());
        setResourcePublicationYear(doiRequest.getResourcePublicationYear());
        setResourceStatus(doiRequest.getResourceStatus());
        setResourceTitle(doiRequest.getResourceTitle());
        setStatus(doiRequest.getStatus());
    }

    public static ExpandedDoiRequest create(DoiRequest doiRequest,
                                            ResourceExpansionServiceImpl resourceExpansionService) {
        ExpandedDoiRequest expandedDoiRequest = new ExpandedDoiRequest(doiRequest);
        Set<URI> ids = resourceExpansionService.getOrganizationIds(expandedDoiRequest.getOwner());
        expandedDoiRequest.setOrganizationIds(ids);
        return expandedDoiRequest;
    }

    @Override
    public Set<URI> getOrganizationIds() {
        return nonNull(organizationIds) ? organizationIds : emptySet();
    }

    @Override
    public void setOrganizationIds(Set<URI> organizationIds) {
        this.organizationIds = organizationIds;
    }

}
