package no.unit.nva.doirequest.create;

import static no.unit.nva.doirequest.DoiRequestsTestConfig.doiRequestsObjectMapper;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.hamcrest.core.IsNot.not;
import static org.hamcrest.core.IsNull.nullValue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import com.amazonaws.services.lambda.runtime.Context;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.net.HttpHeaders;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import no.unit.nva.identifiers.SortableIdentifier;
import no.unit.nva.model.Publication;
import no.unit.nva.model.ResourceOwner;
import no.unit.nva.model.testing.PublicationGenerator;
import no.unit.nva.publication.RequestUtil;
import no.unit.nva.publication.model.ResourceConversation;
import no.unit.nva.publication.model.business.DoiRequest;
import no.unit.nva.publication.model.business.MessageType;
import no.unit.nva.publication.model.business.UserInstance;
import no.unit.nva.publication.service.ResourcesLocalTest;
import no.unit.nva.publication.service.impl.MessageService;
import no.unit.nva.publication.service.impl.ResourceService;
import no.unit.nva.publication.service.impl.TicketService;
import no.unit.nva.testutils.HandlerRequestBuilder;
import nva.commons.apigateway.ApiGatewayHandler;
import nva.commons.apigateway.GatewayResponse;
import nva.commons.apigateway.exceptions.ApiGatewayException;
import nva.commons.apigateway.exceptions.NotFoundException;
import nva.commons.core.Environment;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.zalando.problem.Problem;

class CreateDoiRequestHandlerTest extends ResourcesLocalTest {
    
    public static final String HTTP_PATH_SEPARATOR = "/";
    public static final ResourceOwner NOT_THE_RESOURCE_OWNER = new ResourceOwner(randomString(), randomUri());
    public static final URI SOME_PUBLISHER = URI.create("https://some-publicsher.com");
    public static final int SINGLE_MESSAGE = 0;
    public static final String ALLOW_ALL_ORIGINS = "*";
    private static final Instant PUBLICATION_CREATION_TIME = Instant.parse("2010-01-01T10:15:30.00Z");
    private static final Instant PUBLICATION_UPDATE_TIME = Instant.parse("2011-02-02T10:15:30.00Z");
    private static final Instant DOI_REQUEST_CREATION_TIME = Instant.parse("2012-02-02T10:15:30.00Z");
    private static final Instant DOI_REQUEST_UPDATE_TIME = Instant.parse("2013-02-02T10:15:30.00Z");
    private CreateDoiRequestHandler handler;
    private ResourceService resourceService;
    private Clock mockClock;
    private ByteArrayOutputStream outputStream;
    private Context context;
    private TicketService ticketService;
    private MessageService messageService;
    
    @BeforeEach
    public void initialize() {
        init();
        setupClock();
        resourceService = new ResourceService(client, mockClock);
        ticketService = new TicketService(client);
        messageService = new MessageService(client, mockClock);
        outputStream = new ByteArrayOutputStream();
        context = mock(Context.class);
        Environment environment = mockEnvironment();
        
        handler = new CreateDoiRequestHandler(resourceService, ticketService, messageService, environment);
    }
    
    @Test
    void createDoiRequestStoresNewDoiRequestForPublishedResource()
        throws ApiGatewayException, IOException {
        Publication publication = createPublicationWithoutDoi();
        sendRequest(publication, publication.getResourceOwner());
        var response = GatewayResponse.fromOutputStream(outputStream, Void.class);
        String doiRequestIdentifier = extractLocationHeader(response);
        DoiRequest doiRequest = readDoiRequestDirectlyFromService(new SortableIdentifier(doiRequestIdentifier));
        assertThat(doiRequest, is(not(nullValue())));
    }
    
    @Test
    void createDoiRequestReturnsErrorWhenUserTriesToCreateDoiRequestOnNotOwnedPublication()
        throws IOException {
        Publication publication = createPublicationWithoutDoi();
        
        sendRequest(publication, NOT_THE_RESOURCE_OWNER);
        
        var response = GatewayResponse.fromOutputStream(outputStream, Problem.class);
        Problem problem = response.getBodyObject(Problem.class);
        assertThat(response.getStatusCode(), is(equalTo(HttpStatus.SC_BAD_REQUEST)));
        assertThat(problem.getDetail(), is(equalTo(CreateDoiRequestHandler.USER_IS_NOT_OWNER_ERROR)));
    }
    
    @Test
    void createDoiRequestReturnsBadRequestWhenPublicationIdIsEmpty() throws IOException {
        CreateDoiRequest request = new CreateDoiRequest(null, null);
        InputStream inputStream = new HandlerRequestBuilder<CreateDoiRequest>(doiRequestsObjectMapper)
            .withBody(request)
            .withNvaUsername(NOT_THE_RESOURCE_OWNER.getOwner())
            .withCustomerId(SOME_PUBLISHER)
            .build();
        
        handler.handleRequest(inputStream, outputStream, context);
        var response = GatewayResponse.fromOutputStream(outputStream, Problem.class);
        assertThat(response.getStatusCode(), is(equalTo(HttpStatus.SC_BAD_REQUEST)));
    }
    
    @Test
    void createDoiRequestReturnsBadRequestErrorWenDoiRequestAlreadyExists()
        throws IOException {
        Publication publication = createPublicationWithoutDoi();
        
        sendRequest(publication, publication.getResourceOwner());
        
        outputStream = new ByteArrayOutputStream();
        sendRequest(publication, publication.getResourceOwner());
        
        var response = GatewayResponse.fromOutputStream(outputStream, Problem.class);
        assertThat(response.getStatusCode(), is(equalTo(HttpURLConnection.HTTP_BAD_REQUEST)));
    }
    
    @Test
    void createDoiRequestStoresMessageAsDoiRelatedWhenMessageIsIncluded()
        throws IOException {
        Publication publication = createPublicationWithoutDoi();
        String expectedMessageText = randomString();
        
        sendRequest(publication, publication.getResourceOwner(), expectedMessageText);
        
        Optional<ResourceConversation> resourceMessages = messageService.getMessagesForResource(
            UserInstance.fromPublication(publication),
            publication.getIdentifier());
        
        var savedMessage = resourceMessages.orElseThrow().allMessages().get(SINGLE_MESSAGE);
        assertThat(savedMessage.getText(), is(equalTo(expectedMessageText)));
        assertThat(savedMessage.getMessageType(), is(equalTo(MessageType.DOI_REQUEST)));
    }
    
    public void sendRequest(Publication publication, ResourceOwner owner, String message) throws IOException {
        InputStream inputStream = createRequest(publication, owner, message);
        handler.handleRequest(inputStream, outputStream, context);
    }
    
    private void sendRequest(Publication publication, ResourceOwner owner) throws IOException {
        sendRequest(publication, owner, null);
    }
    
    private Environment mockEnvironment() {
        Environment environment = mock(Environment.class);
        when(environment.readEnv(ApiGatewayHandler.ALLOWED_ORIGIN_ENV)).thenReturn(ALLOW_ALL_ORIGINS);
        return environment;
    }
    
    private InputStream createRequest(Publication publication, ResourceOwner owner, String message)
        throws JsonProcessingException {
        CreateDoiRequest request = new CreateDoiRequest(publication.getIdentifier(), message);
        return new HandlerRequestBuilder<CreateDoiRequest>(doiRequestsObjectMapper)
            .withCustomerId(publication.getPublisher().getId())
            .withNvaUsername(owner.getOwner())
            .withPathParameters(Map.of(RequestUtil.PUBLICATION_IDENTIFIER, publication.getIdentifier().toString()))
            .withBody(request)
            .build();
    }
    
    private DoiRequest readDoiRequestDirectlyFromService(SortableIdentifier doiRequestIdentifier)
        throws NotFoundException {
        return (DoiRequest) ticketService.fetchTicketByIdentifier(doiRequestIdentifier);
    }
    
    private String extractLocationHeader(GatewayResponse<Void> response) {
        String locationHeader = response.getHeaders().get(HttpHeaders.LOCATION);
        String[] headerArray = locationHeader.split(HTTP_PATH_SEPARATOR);
        return headerArray[headerArray.length - 1];
    }
    
    private void setupClock() {
        mockClock = mock(Clock.class);
        when(mockClock.instant())
            .thenReturn(PUBLICATION_CREATION_TIME)
            .thenReturn(PUBLICATION_UPDATE_TIME)
            .thenReturn(DOI_REQUEST_CREATION_TIME)
            .thenReturn(DOI_REQUEST_UPDATE_TIME);
    }
    
    private Publication createPublicationWithoutDoi() {
        Publication publication = PublicationGenerator.randomPublication().copy().withDoi(null).build();
        return resourceService.createPublication(UserInstance.fromPublication(publication), publication);
    }
}
