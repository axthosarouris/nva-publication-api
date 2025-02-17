package no.unit.nva.publication.events.handlers.tickets;

import static java.net.HttpURLConnection.HTTP_NOT_FOUND;
import static java.net.HttpURLConnection.HTTP_OK;
import static no.unit.nva.model.testing.PublicationGenerator.randomPublication;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.hamcrest.core.StringContains.containsString;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Path;
import java.time.Clock;
import no.unit.nva.commons.json.JsonUtils;
import no.unit.nva.events.models.EventReference;
import no.unit.nva.model.Organization;
import no.unit.nva.model.Publication;
import no.unit.nva.model.PublicationStatus;
import no.unit.nva.publication.events.bodies.DataEntryUpdateEvent;
import no.unit.nva.publication.model.business.Entity;
import no.unit.nva.publication.model.business.PublishingRequestCase;
import no.unit.nva.publication.model.business.Resource;
import no.unit.nva.publication.model.business.TicketEntry;
import no.unit.nva.publication.model.business.TicketStatus;
import no.unit.nva.publication.model.business.UserInstance;
import no.unit.nva.publication.service.ResourcesLocalTest;
import no.unit.nva.publication.service.impl.ResourceService;
import no.unit.nva.publication.service.impl.TicketService;
import no.unit.nva.publication.testing.http.FakeHttpClient;
import no.unit.nva.publication.testing.http.FakeHttpResponse;
import no.unit.nva.s3.S3Driver;
import no.unit.nva.stubs.FakeContext;
import no.unit.nva.stubs.FakeS3Client;
import no.unit.nva.testutils.EventBridgeEventBuilder;
import nva.commons.apigateway.exceptions.ApiGatewayException;
import nva.commons.core.ioutils.IoUtils;
import nva.commons.core.paths.UnixPath;
import nva.commons.logutils.LogUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PendingPublishingRequestEventHandlerTest extends ResourcesLocalTest {
    
    public static final Entity EMPTY = null;
    private static final URI CUSTOMER_ID = randomUri();
    private S3Driver s3Driver;
    private FakeS3Client s3Client;
    private PendingPublishingRequestEventHandler handler;
    private ByteArrayOutputStream output;
    private FakeContext context;
    private ResourceService resourceService;
    private TicketService ticketService;
    private FakeHttpClient<String> httpClient;
    
    @BeforeEach
    public void setup() {
        super.init();
        s3Client = new FakeS3Client();
        this.s3Driver = new S3Driver(s3Client, randomString());
        this.resourceService = new ResourceService(client, Clock.systemDefaultZone());
        this.ticketService = new TicketService(client);
        
        this.output = new ByteArrayOutputStream();
        this.context = new FakeContext();
    }
    
    @Test
    void shouldApprovePublishingRequestAutomaticallyWhenCustomerHasPolicyAllowingRegistratorsToPublishDataAndMetadata()
        throws IOException, ApiGatewayException {
        var publishingRequest = pendingPublishingRequest();
        var event = createEvent(publishingRequest);
        var customerAllowingPublishing =
            mockIdentityServiceResponseForPublisherAllowingAutomaticPublishingRequestsAprroval();
        this.httpClient = new FakeHttpClient<>(FakeHttpResponse.create(customerAllowingPublishing, HTTP_OK));
    
        this.handler = new PendingPublishingRequestEventHandler(ticketService, httpClient, s3Client);
        handler.handleRequest(event, output, context);
        var updatedPublishingRequest = ticketService.fetchTicket(publishingRequest);
        assertThat(updatedPublishingRequest.getStatus(), is(equalTo(TicketStatus.COMPLETED)));
    }
    
    @Test
    void shouldIgnorePublishingRequestWhenCustomerHasPolicyForbiddingRegistratorsToPublishDataAndMetadata()
        throws IOException, ApiGatewayException {
        var publishingRequest = pendingPublishingRequest();
        var event = createEvent(publishingRequest);
    
        var customerAllowingPublishing =
            mockIdentityServiceResponseForCustomersThatRequireManualApprovalOfPublishingRequests();
        this.httpClient = new FakeHttpClient<>(FakeHttpResponse.create(customerAllowingPublishing, HTTP_OK));
    
        this.handler = new PendingPublishingRequestEventHandler(ticketService, httpClient, s3Client);
        handler.handleRequest(event, output, context);
        var updatedPublishingRequest = ticketService.fetchTicket(publishingRequest);
        assertThat(updatedPublishingRequest.getStatus(), is(equalTo(TicketStatus.PENDING)));
    }
    
    @Test
    void shouldIgnorePublishingRequestAndLogResponseWhenCustomerCannotBeResolved()
        throws IOException, ApiGatewayException {
        var publishingRequest = pendingPublishingRequest();
        var event = createEvent(publishingRequest);
        final var logger = LogUtils.getTestingAppenderForRootLogger();
        
        var identityServiceResponse = unresolvableCustomer();
        this.httpClient = new FakeHttpClient<>(identityServiceResponse);
        this.handler = new PendingPublishingRequestEventHandler(ticketService, httpClient, s3Client);
        
        handler.handleRequest(event, output, context);
        var updatedPublishingRequest = ticketService.fetchTicket(publishingRequest);
        assertThat(updatedPublishingRequest.getStatus(), is(equalTo(TicketStatus.PENDING)));
        assertThat(logger.getMessages(), containsString(identityServiceResponse.body()));
    }
    
    @Test
    void shouldNotCompleteAlreadyCompletedTicketsAndEnterInfiniteLoop() throws ApiGatewayException, IOException {
        var completedTicket = createCompletedTicketEntry();
        var versionBeforeEvent = completedTicket.toDao().fetchByIdentifier(client).getVersion();
        callApiOfCustomerAllowingAutomaticPublishing((PublishingRequestCase) completedTicket);
        var versionAfterEvent = completedTicket.toDao().fetchByIdentifier(client).getVersion();
        assertThat(versionAfterEvent, is(equalTo(versionBeforeEvent)));
    }

    private void callApiOfCustomerAllowingAutomaticPublishing(PublishingRequestCase completedTicket)
            throws IOException {
        var customerAllowingPublishing =
                mockIdentityServiceResponseForPublisherAllowingAutomaticPublishingRequestsAprroval();

        var event = createEvent(completedTicket);

        this.httpClient = new FakeHttpClient<>(FakeHttpResponse.create(customerAllowingPublishing, HTTP_OK));
        this.handler = new PendingPublishingRequestEventHandler(ticketService, httpClient, s3Client);
        handler.handleRequest(event, output, context);
    }

    private TicketEntry createCompletedTicketEntry() throws ApiGatewayException {
        var publication = createPublication();
        var completedTicket = TicketEntry.requestNewTicket(publication, PublishingRequestCase.class)
                                  .persistNewTicket(ticketService)
                                  .complete(publication);
        completedTicket = ticketService.updateTicketStatus(completedTicket, TicketStatus.COMPLETED);
        return completedTicket;
    }

    private static String mockIdentityServiceResponseForCustomersThatRequireManualApprovalOfPublishingRequests() {
        return IoUtils.stringFromResources(Path.of("publishingrequests", "customers",
            "customer_forbidding_publishing.json"));
    }
    
    private static FakeHttpResponse<String> unresolvableCustomer() {
        return FakeHttpResponse.create(randomString(), HTTP_NOT_FOUND);
    }
    
    private static String mockIdentityServiceResponseForPublisherAllowingAutomaticPublishingRequestsAprroval() {
        return IoUtils.stringFromResources(Path.of("publishingrequests", "customers",
            "customer_allowing_publishing.json"));
    }
    
    private InputStream createEvent(PublishingRequestCase publishingRequest) throws IOException {
        var blobUri = createEventBlob(publishingRequest);
        var event = new EventReference(DataEntryUpdateEvent.PUBLISHING_REQUEST_UPDATE_EVENT_TOPIC, blobUri);
        return EventBridgeEventBuilder.sampleLambdaDestinationsEvent(event);
    }
    
    private URI createEventBlob(PublishingRequestCase publishingRequest) throws IOException {
        var dataEntryUpdateEvent = firstAppearanceOfAPublishingRequest(publishingRequest);
        var blobContent = JsonUtils.dtoObjectMapper.writeValueAsString(dataEntryUpdateEvent);
        return s3Driver.insertEvent(UnixPath.of(randomString()), blobContent);
    }
    
    private DataEntryUpdateEvent firstAppearanceOfAPublishingRequest(PublishingRequestCase publishingRequest) {
        return new DataEntryUpdateEvent(randomString(), EMPTY, publishingRequest);
    }
    
    private PublishingRequestCase pendingPublishingRequest() throws ApiGatewayException {
        var publication = createPublication();
        return createPendingPublishingRequest(publication);
    }
    
    private PublishingRequestCase createPendingPublishingRequest(Publication publication) throws ApiGatewayException {
        var publishingRequest = PublishingRequestCase.createOpeningCaseObject(publication);
        return (PublishingRequestCase) publishingRequest.persistNewTicket(ticketService);
    }
    
    private Publication createPublication() {
        var publication = randomPublication();
        publication.setStatus(PublicationStatus.DRAFT);
        publication.setPublisher(new Organization.Builder().withId(CUSTOMER_ID).build());
        publication = Resource.fromPublication(publication)
                          .persistNew(resourceService, UserInstance.fromPublication(publication));
        return publication;
    }
}