package no.unit.nva.publication.service.impl;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import no.unit.nva.publication.external.services.PersonApiClient;
import no.unit.nva.publication.utils.OrgUnitId;
import nva.commons.apigateway.exceptions.ApiGatewayException;

public final class AffiliationSelectionService {

    private final PersonApiClient personApiClient;

    private AffiliationSelectionService(PersonApiClient personApiClient) {
        this.personApiClient = personApiClient;
    }

    public static AffiliationSelectionService create(HttpClient httpClientToExternalServices) {
        return new AffiliationSelectionService(new PersonApiClient(httpClientToExternalServices));
    }

    public Optional<URI> fetchAffiliation(String feideId)
        throws IOException, ApiGatewayException, InterruptedException {
        var affiliations = fetchAffiliationUris(feideId);
        return OrgUnitId.extractMostLikelyAffiliationForUser(affiliations)
            .map(OrgUnitId::getUnitId);
    }

    private List<OrgUnitId> fetchAffiliationUris(String feideId)
        throws IOException, InterruptedException, ApiGatewayException {
        return personApiClient.fetchAffiliationsForUser(feideId)
            .stream()
            .map(OrgUnitId::new)
            .collect(Collectors.toList());
    }
}
