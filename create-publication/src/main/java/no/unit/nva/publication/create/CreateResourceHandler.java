package no.unit.nva.publication.create;

import com.amazonaws.services.lambda.runtime.Context;
import java.net.HttpURLConnection;
import java.net.URI;
import java.time.Instant;
import no.unit.nva.model.Organization;
import no.unit.nva.model.PublicationStatus;
import no.unit.nva.publication.identifiers.SortableIdentifier;
import no.unit.nva.publication.service.impl.ResourceService;
import no.unit.nva.publication.storage.model.Resource;
import nva.commons.handlers.ApiGatewayHandler;
import nva.commons.handlers.RequestInfo;
import org.slf4j.LoggerFactory;

public class CreateResourceHandler extends ApiGatewayHandler<Void, Resource> {

    private final ResourceService resourceService;

    public CreateResourceHandler() {
        super(Void.class, LoggerFactory.getLogger(CreateResourceHandler.class));
        resourceService = new ResourceService();
    }

    @Override
    protected Resource processInput(Void input, RequestInfo requestInfo, Context context) {
        String feideId = requestInfo.getFeideId().orElseThrow();
        URI customerId = getCustomerUri(requestInfo);
        logger.info("FeideId:" + feideId);
        logger.info("CustomerId:" + customerId);
        Resource resource = new Resource();
        resource.setIdentifier(SortableIdentifier.next());
        resource.setPublisher(newOrganization(customerId));
        resource.setOwner(feideId);
        resource.setStatus(PublicationStatus.DRAFT);
        resource.setCreatedDate(Instant.now());
        resourceService.createResource(resource);
        return resource;
    }

    private Organization newOrganization(URI customerId) {
        return new Organization.Builder().withId(customerId).build();
    }

    private URI getCustomerUri(RequestInfo requestInfo) {
        return requestInfo.getCustomerId()
            .map(URI::create)
            .orElseThrow();
    }

    @Override
    protected Integer getSuccessStatusCode(Void input, Resource output) {
        return HttpURLConnection.HTTP_ACCEPTED;
    }
}