package no.unit.nva.publication;

import com.amazonaws.services.lambda.runtime.Context;
import com.fasterxml.jackson.databind.ObjectMapper;
import no.unit.nva.Environment;
import no.unit.nva.GatewayResponse;
import no.unit.nva.PublicationHandler;
import no.unit.nva.model.PublicationSummary;
import no.unit.nva.service.PublicationService;
import org.apache.http.HttpHeaders;
import org.apache.http.entity.ContentType;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.EnvironmentVariables;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static java.util.Collections.singletonMap;
import static no.unit.nva.PublicationHandler.ACCESS_CONTROL_ALLOW_ORIGIN;
import static no.unit.nva.PublicationHandler.ALLOWED_ORIGIN_ENV;
import static no.unit.nva.model.PublicationStatus.DRAFT;
import static no.unit.nva.service.impl.DynamoDBPublicationService.BY_PUBLISHER_INDEX_NAME_ENV;
import static no.unit.nva.service.impl.DynamoDBPublicationService.TABLE_NAME_ENV;
import static org.apache.http.HttpHeaders.CONTENT_TYPE;
import static org.apache.http.HttpStatus.SC_BAD_GATEWAY;
import static org.apache.http.HttpStatus.SC_BAD_REQUEST;
import static org.apache.http.HttpStatus.SC_INTERNAL_SERVER_ERROR;
import static org.apache.http.HttpStatus.SC_OK;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class PublicationsByOwnerHandlerTest {

    public static final String OWNER = "junit";
    public static final String VALID_ORG_NUMBER = "NO919477822";
    private ObjectMapper objectMapper = PublicationHandler.createObjectMapper();

    @Mock
    private Environment environment;

    @Mock
    private PublicationService publicationService;

    @Mock
    private Context context;

    private OutputStream output;
    private PublicationsByOwnerHandler publicationsByOwnerHandler;

    /**
     * Set up environment.
     */
    @Before
    public void setUp() {
        when(environment.get(ALLOWED_ORIGIN_ENV)).thenReturn(Optional.of("*"));

        output = new ByteArrayOutputStream();
        publicationsByOwnerHandler =
                new PublicationsByOwnerHandler(objectMapper, publicationService, environment);

    }

    @Rule
    public final EnvironmentVariables environmentVariables
            = new EnvironmentVariables();

    @Test
    public void testDefaultConstructor() {
        environmentVariables.set(ALLOWED_ORIGIN_ENV, "*");
        environmentVariables.set(TABLE_NAME_ENV, "nva_resources");
        environmentVariables.set(BY_PUBLISHER_INDEX_NAME_ENV, "ByPublisher");
        PublicationsByOwnerHandler publicationsByOwnerHandler = new PublicationsByOwnerHandler();
        assertNotNull(publicationsByOwnerHandler);
    }

    @Test
    public void testOkResponse() throws IOException, InterruptedException {
        when(publicationService.getPublicationsByOwner(anyString(), any(URI.class), any()))
                .thenReturn(createPublicationSummaries());

        publicationsByOwnerHandler.handleRequest(
                inputStream(), output, context);

        GatewayResponse gatewayResponse = objectMapper.readValue(output.toString(), GatewayResponse.class);
        assertEquals(SC_OK, gatewayResponse.getStatusCode());
        Assert.assertTrue(gatewayResponse.getHeaders().keySet().contains(CONTENT_TYPE));
        Assert.assertTrue(gatewayResponse.getHeaders().keySet().contains(ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    public void testBadRequestResponse() throws IOException {
        publicationsByOwnerHandler.handleRequest(
                new ByteArrayInputStream(new byte[]{}), output, context);

        GatewayResponse gatewayResponse = objectMapper.readValue(output.toString(), GatewayResponse.class);
        assertEquals(SC_BAD_REQUEST, gatewayResponse.getStatusCode());
    }

    @Test
    public void testBadGateWayResponse() throws IOException, InterruptedException {
        when(publicationService.getPublicationsByOwner(anyString(), any(URI.class), any()))
                .thenThrow(IOException.class);

        publicationsByOwnerHandler.handleRequest(
                inputStream(), output, context);

        GatewayResponse gatewayResponse = objectMapper.readValue(output.toString(), GatewayResponse.class);
        assertEquals(SC_BAD_GATEWAY, gatewayResponse.getStatusCode());
    }

    @Test
    public void testInternalServerErrorResponse() throws IOException, InterruptedException {
        when(publicationService.getPublicationsByOwner(anyString(), any(URI.class), any()))
                .thenThrow(NullPointerException.class);

        publicationsByOwnerHandler.handleRequest(
                inputStream(), output, context);

        GatewayResponse gatewayResponse = objectMapper.readValue(output.toString(), GatewayResponse.class);
        assertEquals(SC_INTERNAL_SERVER_ERROR, gatewayResponse.getStatusCode());
    }

    private InputStream inputStream() throws IOException {
        Map<String, Object> event = new HashMap<>();
        event.put("requestContext",
                singletonMap("authorizer",
                        singletonMap("claims",
                                Map.of("custom:feideId", OWNER, "custom:orgNumber", VALID_ORG_NUMBER))));
        event.put("headers", singletonMap(HttpHeaders.CONTENT_TYPE,
                ContentType.APPLICATION_JSON.getMimeType()));
        return new ByteArrayInputStream(objectMapper.writeValueAsBytes(event));
    }

    private List<PublicationSummary> createPublicationSummaries() {
        List<PublicationSummary> publicationSummaries = new ArrayList<>();
        publicationSummaries.add(new PublicationSummary.Builder()
                .withIdentifier(UUID.randomUUID())
                .withModifiedDate(Instant.now())
                .withCreatedDate(Instant.now())
                .withOwner("junit")
                .withMainTitle("Some main title")
                .withStatus(DRAFT)
                .build()
        );
        publicationSummaries.add(new PublicationSummary.Builder()
                .withIdentifier(UUID.randomUUID())
                .withModifiedDate(Instant.now())
                .withCreatedDate(Instant.now())
                .withOwner(OWNER)
                .withMainTitle("A complete different title")
                .withStatus(DRAFT)
                .build()
        );
        return publicationSummaries;
    }

}
