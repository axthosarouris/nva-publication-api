package no.unit.nva.publication.create;

import static no.unit.nva.publication.create.CreatePublicationHandler.API_HOST;
import static no.unit.nva.publication.create.CreatePublicationHandler.API_SCHEME;
import static no.unit.nva.publication.testing.TestHeaders.getRequestHeaders;
import static no.unit.nva.publication.testing.TestHeaders.getResponseHeaders;

import static nva.commons.apigateway.ApiGatewayHandler.ALLOWED_ORIGIN_ENV;
import static nva.commons.core.JsonUtils.objectMapper;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.amazonaws.services.lambda.runtime.Context;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.JavaType;
import no.unit.nva.PublicationMapper;
import no.unit.nva.api.CreatePublicationRequest;
import no.unit.nva.api.PublicationResponse;
import no.unit.nva.model.Organization;
import no.unit.nva.model.Publication;
import no.unit.nva.publication.RequestUtil;
import no.unit.nva.publication.service.PublicationService;
import nva.commons.apigateway.GatewayResponse;
import nva.commons.core.Environment;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.migrationsupport.rules.EnableRuleMigrationSupport;

@EnableRuleMigrationSupport
public class CreatePublicationHandlerTest {

    public static final String HTTPS = "https";
    public static final String NVA_UNIT_NO = "nva.unit.no";
    public static final String WILDCARD = "*";
    public static final String AUTHORIZER = "authorizer";
    public static final String CLAIMS = "claims";
    public static final String TEST_FEIDE_ID = "junit";
    public static final String TEST_ORG_NUMBER = "NO919477822";
    public static final String REQUEST_CONTEXT = "requestContext";
    public static final String HEADERS = "headers";
    public static final String BODY = "body";

    private PublicationService publicationServiceMock;
    private CreatePublicationHandler handler;
    private ByteArrayOutputStream outputStream;
    private Context context;
    public static final JavaType PARAMETERIZED_GATEWAY_RESPONSE_TYPE = objectMapper.getTypeFactory()
            .constructParametricType(GatewayResponse.class, PublicationResponse.class);

    /**
     * Setting up test environment.
     */
    @BeforeEach
    public void setUp() {
        publicationServiceMock = mock(PublicationService.class);
        Environment environmentMock = mock(Environment.class);
        when(environmentMock.readEnv(ALLOWED_ORIGIN_ENV)).thenReturn(WILDCARD);
        when(environmentMock.readEnv(API_SCHEME)).thenReturn(HTTPS);
        when(environmentMock.readEnv(API_HOST)).thenReturn(NVA_UNIT_NO);
        handler = new CreatePublicationHandler(publicationServiceMock, environmentMock);
        outputStream = new ByteArrayOutputStream();
        context = mock(Context.class);
    }

    @Test
    public void requestToHandlerReturnsCustomerCreated() throws Exception {
        Publication publication = createPublication();
        when(publicationServiceMock.createPublication(any(Publication.class))).thenReturn(publication);

        CreatePublicationRequest request = new CreatePublicationRequest();
        request.setEntityDescription(publication.getEntityDescription());
        request.setProjects(publication.getProjects());
        InputStream inputStream = createPublicationRequest(request);
        handler.handleRequest(inputStream, outputStream, context);

        GatewayResponse<PublicationResponse> actual = objectMapper.readValue(outputStream.toByteArray(),
                PARAMETERIZED_GATEWAY_RESPONSE_TYPE);

        GatewayResponse<PublicationResponse> expected = new GatewayResponse<>(
            PublicationMapper.convertValue(publication, PublicationResponse.class),
            getResponseHeadersWithLocation(publication.getIdentifier()),
            HttpStatus.SC_CREATED
        );

        assertEquals(expected, actual);
    }

    private Map<String,String> getResponseHeadersWithLocation(UUID identifier) {
        Map<String, String> map = new HashMap<>(getResponseHeaders());
        map.put(HttpHeaders.LOCATION, handler.getLocation(identifier).toString());
        return map;
    }

    @Test
    public void canCreateNewPublication() throws Exception {
        Publication publication = createPublication();
        when(publicationServiceMock.createPublication(any(Publication.class))).thenReturn(publication);

        InputStream inputStream = emptyCreatePublicationRequest();
        handler.handleRequest(inputStream, outputStream, context);
        GatewayResponse<PublicationResponse> actual = objectMapper.readValue(outputStream.toByteArray(),
                PARAMETERIZED_GATEWAY_RESPONSE_TYPE);

        assertEquals(HttpStatus.SC_CREATED, actual.getStatusCode());
        assertNotNull(actual.getBodyObject(PublicationResponse.class));
    }

    private InputStream createPublicationRequest(CreatePublicationRequest request) throws JsonProcessingException {
        Map<String, Object> map = Map.of(
            REQUEST_CONTEXT, createRequestContext(),
            HEADERS, getRequestHeaders(),
            BODY, request
        );
        return new ByteArrayInputStream(objectMapper.writeValueAsBytes(map));
    }

    private InputStream emptyCreatePublicationRequest() throws JsonProcessingException {
        Map<String, Object> map = Map.of(
            REQUEST_CONTEXT, createRequestContext(),
            HEADERS, getRequestHeaders()
        );
        return new ByteArrayInputStream(objectMapper.writeValueAsBytes(map));
    }

    private Map<String, Map<String, Map<String, String>>> createRequestContext() {
        return Map.of(
            AUTHORIZER, Map.of(
                CLAIMS, Map.of(
                    RequestUtil.CUSTOM_FEIDE_ID, TEST_FEIDE_ID,
                    RequestUtil.CUSTOM_CUSTOMER_ID, TEST_ORG_NUMBER
                )
            )
        );
    }

    private Publication createPublication() {
        return new Publication.Builder()
            .withIdentifier(UUID.randomUUID())
            .withModifiedDate(Instant.now())
            .withOwner("owner")
            .withPublisher(new Organization.Builder()
                .withId(URI.create("http://example.org/publisher/1"))
                .build()
            )
            .build();
    }
}
