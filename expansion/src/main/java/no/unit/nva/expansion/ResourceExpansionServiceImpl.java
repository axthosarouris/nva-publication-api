package no.unit.nva.expansion;

import static no.unit.nva.expansion.OrganizationResponseObject.retrieveAllRelatedOrganizations;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.net.URI;
import java.net.http.HttpClient;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import no.unit.nva.expansion.model.ExpandedDataEntry;
import no.unit.nva.expansion.model.ExpandedDoiRequest;
import no.unit.nva.expansion.model.ExpandedMessage;
import no.unit.nva.expansion.model.ExpandedResource;
import no.unit.nva.publication.service.impl.ResourceService;
import no.unit.nva.publication.storage.model.ConnectedToResource;
import no.unit.nva.publication.storage.model.DataEntry;
import no.unit.nva.publication.storage.model.DoiRequest;
import no.unit.nva.publication.storage.model.Message;
import no.unit.nva.publication.storage.model.Resource;
import nva.commons.apigateway.exceptions.NotFoundException;

public class ResourceExpansionServiceImpl implements ResourceExpansionService {

    public static final String UNSUPPORTED_TYPE = "Expansion is not supported for type:";

    private final ResourceService resourceService;
    private final HttpClient externalServicesHttpClient;

    public ResourceExpansionServiceImpl(HttpClient externalServicesHttpClient,
                                        ResourceService resourceService) {
        this.externalServicesHttpClient = externalServicesHttpClient;
        this.resourceService = resourceService;
    }

    @Override
    public ExpandedDataEntry expandEntry(DataEntry dataEntry) throws JsonProcessingException, NotFoundException {
        if (dataEntry instanceof Resource) {
            return ExpandedResource.fromPublication(dataEntry.toPublication());
        } else if (dataEntry instanceof DoiRequest) {
            return ExpandedDoiRequest.create((DoiRequest) dataEntry, this);
        } else if (dataEntry instanceof Message) {
            return ExpandedMessage.create((Message) dataEntry, this);
        }
        // will throw exception if we want to index a new type that we are not handling yet
        throw new UnsupportedOperationException(UNSUPPORTED_TYPE + dataEntry.getClass().getSimpleName());
    }

    @Override
    public Set<URI> getOrganizationIds(DataEntry dataEntry) throws NotFoundException {
        if (dataEntry instanceof ConnectedToResource) {
            var resourceIdentifier = ((ConnectedToResource) dataEntry).getResourceIdentifier();
            var resource = resourceService.getResourceByIdentifier(resourceIdentifier);
            return Optional.ofNullable(resource.getResourceOwner().getOwnerAffiliation())
                .stream()
                .map(affiliation -> retrieveAllRelatedOrganizations(externalServicesHttpClient, affiliation))
                .flatMap(Collection::stream)
                .collect(Collectors.toSet());
        }
        return Collections.emptySet();
    }
}
