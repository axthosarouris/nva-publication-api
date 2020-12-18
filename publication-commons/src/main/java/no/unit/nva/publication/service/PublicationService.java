package no.unit.nva.publication.service;

import no.unit.nva.model.Publication;
import no.unit.nva.publication.model.PublicationSummary;
import no.unit.nva.publication.model.PublishPublicationStatusResponse;
import nva.commons.exceptions.ApiGatewayException;

import java.net.URI;
import java.util.List;
import java.util.UUID;

public interface PublicationService {

    Publication createPublication(Publication publication) throws ApiGatewayException;

    Publication getPublication(UUID identifier)
        throws ApiGatewayException;

    Publication updatePublication(UUID identifier, Publication publication)
        throws ApiGatewayException;

    List<PublicationSummary> getPublicationsByPublisher(URI publisherId)
        throws ApiGatewayException;

    List<PublicationSummary> getPublicationsByOwner(String owner, URI publisherId)
        throws ApiGatewayException;

    List<PublicationSummary> listPublishedPublicationsByDate(int pageSize)
            throws ApiGatewayException;

    PublishPublicationStatusResponse publishPublication(UUID identifier)
        throws ApiGatewayException;

    void markPublicationForDeletion(UUID identifier, String owner) throws ApiGatewayException;
}
