package no.unit.nva.publication.events;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import com.amazonaws.services.lambda.runtime.Context;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import no.unit.nva.model.Publication;
import nva.commons.core.JsonUtils;
import nva.commons.core.attempt.Try;
import nva.commons.core.ioutils.IoUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

public class PublicationFanoutHandlerTest {

    private static final ObjectMapper objectMapper = JsonUtils.objectMapper;
    public static final String DYNAMODBEVENT_NEW_IMAGE_JSON = "dynamodbevent_new_image.json";
    public static final String DYNAMODBEVENT_INVALID_IMAGE_JSON = "dynamodbevent_invalid_image.json";
    public static final String DYNAMODBEVENT_NEW_AND_OLD_IMAGES_JSON = "dynamodbevent_new_and_old_images.json";
    public static final String DYNAMODBEVENT_OLD_IMAGE_JSON = "dynamodbevent_old_image.json";
    public static final String DYNAMODBEVENT_EMPTY_ATTRIBUTE_VALUE_JSON = "dynamodbevent_empty_attribute_value.json";

    private OutputStream outputStream;
    private Context context;

    @BeforeEach
    public void setUp() {
        outputStream = new ByteArrayOutputStream();
        context = Mockito.mock(Context.class);
    }

    @Test
    public void handleRequestReturnsPublicationUpdateEventWhenEventContainsOnlyNewImage() {
        PublicationFanoutHandler handler = new PublicationFanoutHandler();

        InputStream inputStream = IoUtils.inputStreamFromResources(
                DYNAMODBEVENT_NEW_IMAGE_JSON);

        handler.handleRequest(inputStream, outputStream, context);

        DynamoEntryUpdateEvent response = parseResponse();

        assertThat(response.getOldPublication(), is(nullValue()));
        assertThat(response.getNewPublication(), is(notNullValue()));
    }

    @Test
    public void handleRequestReturnsPublicationUpdateEventWhenEventContainsOnlyOldImage() {
        PublicationFanoutHandler handler = new PublicationFanoutHandler();

        InputStream inputStream = IoUtils.inputStreamFromResources(
                DYNAMODBEVENT_OLD_IMAGE_JSON);

        handler.handleRequest(inputStream, outputStream, context);

        DynamoEntryUpdateEvent response = parseResponse();

        assertThat(response.getOldPublication(), is(notNullValue()));
        assertThat(response.getNewPublication(), is(nullValue()));
    }

    @Test
    public void handleRequestReturnsPublicationUpdateEventWhenEventContainsNewAndOldImage() {
        PublicationFanoutHandler handler = new PublicationFanoutHandler();

        InputStream inputStream = IoUtils.inputStreamFromResources(
                DYNAMODBEVENT_NEW_AND_OLD_IMAGES_JSON);

        handler.handleRequest(inputStream, outputStream, context);

        DynamoEntryUpdateEvent response = parseResponse();

        assertThat(response.getOldPublication(), is(notNullValue()));
        assertThat(response.getNewPublication(), is(notNullValue()));
    }

    @Test
    public void handleRequestThrowsRuntimeExceptionWhenImageIsInvalid() {
        PublicationFanoutHandler handler = new PublicationFanoutHandler();

        InputStream inputStream = IoUtils.inputStreamFromResources(
            DYNAMODBEVENT_INVALID_IMAGE_JSON);

        RuntimeException exception = assertThrows(RuntimeException.class,
            () -> handler.handleRequest(inputStream, outputStream, context));

        assertThat(exception.getMessage(), containsString(PublicationFanoutHandler.MAPPING_ERROR));
    }

    @Test
    public void handleRequestReturnsPublicationUpdateEventWhenImageHasEmptyAttributeValues() {
        PublicationFanoutHandler handler = new PublicationFanoutHandler();

        InputStream inputStream = IoUtils.inputStreamFromResources(
                DYNAMODBEVENT_EMPTY_ATTRIBUTE_VALUE_JSON);

        handler.handleRequest(inputStream, outputStream, context);

        DynamoEntryUpdateEvent response = parseResponse();

        assertThat(response.getOldPublication(), is(nullValue()));
        assertThat(response.getNewPublication(), is(notNullValue()));

        Publication publication = response.getNewPublication();

        assertThat(publication.getEntityDescription(), is(notNullValue()));
        assertThat(publication.getEntityDescription().getReference(), is(notNullValue()));

        assertThat(publication.getEntityDescription().getReference().getDoi(), is(nullValue()));
        assertThat(publication.getEntityDescription().getLanguage(), is(nullValue()));
    }

    private DynamoEntryUpdateEvent parseResponse() {
        return Try.attempt(() -> objectMapper.readValue(outputStream.toString(), DynamoEntryUpdateEvent.class))
            .orElseThrow();
    }
}
