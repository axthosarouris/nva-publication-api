package no.unit.nva;

import com.amazonaws.services.lambda.runtime.Context;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import no.unit.nva.model.PublicationDate;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.zalando.problem.Status;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;
import java.util.Optional;

import static no.unit.nva.PublicationHandler.ACCESS_CONTROL_ALLOW_ORIGIN;
import static no.unit.nva.PublicationHandler.ALLOWED_ORIGIN_ENV;
import static no.unit.nva.PublicationHandler.APPLICATION_JSON;
import static no.unit.nva.PublicationHandler.CONTENT_TYPE;
import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

public class PublicationHandlerTest {

    public static final String MISSING_FILE_JSON = "missing_file.json";
    public static final String WILDCARD = "*";

    private OutputStream outputStream;
    private ObjectMapper objectMapper = PublicationHandler.createObjectMapper();
    private Environment environment;

    private PublicationHandler publicationHandler;

    /**
     * Prepare test instances.
     */
    @BeforeEach
    public void setUp() {
        environment = Mockito.mock(Environment.class);
        when(environment.get(ALLOWED_ORIGIN_ENV)).thenReturn(Optional.of(WILDCARD));
        publicationHandler = new TestPublicationHandler(objectMapper, environment);
        outputStream = new ByteArrayOutputStream();
    }

    @Test
    public void testMissingPublicationContext() {
        Optional<JsonNode> publicationContext = publicationHandler
                .getPublicationContext(MISSING_FILE_JSON);
        assertTrue(publicationContext.isEmpty());
    }

    @Test
    public void testHeaders() {
        Map<String, String> headers = publicationHandler.headers();
        assertEquals(headers.get(ACCESS_CONTROL_ALLOW_ORIGIN), WILDCARD);
        assertEquals(headers.get(CONTENT_TYPE), APPLICATION_JSON);
    }

    @Test
    public void testWriteErrorResponse() throws IOException {
        String message = "Test!";
        Exception exception = new Exception(message);
        publicationHandler.writeErrorResponse(outputStream, Status.INTERNAL_SERVER_ERROR, exception);

        GatewayResponse gatewayResponse = objectMapper.readValue(outputStream.toString(), GatewayResponse.class);

        Assertions.assertNotNull(gatewayResponse);

    }

    @Test
    public void mappingEmptyStringAsNull() throws JsonProcessingException {
        String json =  "{ \"type\" : \"PublicationDate\", \"year\" : \"2019\", \"month\" : \"\", \"day\" : \"\"}";
        ObjectMapper objectMapper = PublicationHandler.createObjectMapper();
        PublicationDate publicationDate = objectMapper.readValue(json, PublicationDate.class);

        assertNull(publicationDate.getMonth());
        assertNull(publicationDate.getDay());
    }

    public static class TestPublicationHandler extends PublicationHandler {

        public TestPublicationHandler(ObjectMapper objectMapper, Environment environment) {
            super(objectMapper, environment);
        }

        @Override
        public void handleRequest(InputStream input, OutputStream output, Context context) throws IOException {

        }
    }

}
