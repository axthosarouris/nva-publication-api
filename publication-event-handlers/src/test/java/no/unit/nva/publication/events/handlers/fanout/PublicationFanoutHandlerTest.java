package no.unit.nva.publication.events.handlers.fanout;

import static no.unit.nva.publication.events.handlers.PublicationEventsConfig.objectMapper;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.collection.IsEmptyCollection.empty;
import static org.hamcrest.core.IsNot.not;
import static org.junit.jupiter.api.Assertions.assertThrows;
import com.amazonaws.services.lambda.runtime.Context;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.List;
import no.unit.nva.identifiers.SortableIdentifier;
import no.unit.nva.model.Contributor;
import no.unit.nva.model.Publication;
import no.unit.nva.model.pages.Pages;
import no.unit.nva.publication.events.bodies.DynamoEntryUpdateEvent;
import nva.commons.core.attempt.Try;
import nva.commons.core.ioutils.IoUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

public class PublicationFanoutHandlerTest {

    public static final String DYNAMODBEVENT_NEW_IMAGE_JSON = "dynamodbevent_new_image.json";
    public static final String DYNAMODBEVENT_INVALID_IMAGE_JSON = "dynamodbevent_invalid_image.json";
    public static final String DYNAMODBEVENT_NEW_AND_OLD_IMAGES_JSON = "dynamodbevent_new_and_old_images.json";
    public static final String DYNAMODBEVENT_OLD_IMAGE_JSON = "dynamodbevent_old_image.json";
    public static final String DYNAMODBEVENT_EMPTY_ATTRIBUTE_VALUE_JSON = "dynamodbevent_empty_attribute_value.json";
    public static final String DYNAMODBEVENT_UNIQUENESS_ENTRY = "dynamodbevent_uniqueness_entry.json";
    private static final SortableIdentifier IDENTIFIER_IN_RESOURCE =
        new SortableIdentifier("0177627d7889-8e380cb5-6851-43fc-b05d-c85f26967270");

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

        assertThat(response.getOldData(), is(nullValue()));
        assertThat(response.getNewData().toPublication(), is(notNullValue()));

        assertThat(response.getNewData().toPublication().getIdentifier(), is(equalTo(IDENTIFIER_IN_RESOURCE)));
    }

    @Test
    public void handleRequestReturnsPublicationUpdateEventWhenEventContainsOnlyOldImage() {
        PublicationFanoutHandler handler = new PublicationFanoutHandler();

        InputStream inputStream = IoUtils.inputStreamFromResources(
                DYNAMODBEVENT_OLD_IMAGE_JSON);

        handler.handleRequest(inputStream, outputStream, context);

        DynamoEntryUpdateEvent response = parseResponse();

        assertThat(response.getOldData().toPublication(), is(notNullValue()));
        assertThat(response.getNewData(), is(nullValue()));
    }

    @Test
    public void handleRequestReturnsPublicationUpdateEventWhenEventContainsNewAndOldImage() {
        PublicationFanoutHandler handler = new PublicationFanoutHandler();

        InputStream inputStream = IoUtils.inputStreamFromResources(
            DYNAMODBEVENT_NEW_AND_OLD_IMAGES_JSON);

        handler.handleRequest(inputStream, outputStream, context);

        DynamoEntryUpdateEvent response = parseResponse();

        assertThat(response.getOldData().toPublication(), is(notNullValue()));
        assertThat(response.getNewData().toPublication(), is(notNullValue()));

        assertThat(response.getNewData().toPublication().getEntityDescription(), is(notNullValue()));
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

        assertThat(response.getOldData(), is(nullValue()));
        assertThat(response.getNewData().toPublication(), is(notNullValue()));

        Publication publication = response.getNewData().toPublication();

        assertThat(publication.getEntityDescription(), is(notNullValue()));
        assertThat(publication.getEntityDescription().getReference(), is(notNullValue()));

        assertThat(fieldWithValueEmptyObject(publication), is(nullValue()));
        assertThat(fieldWithValueEmptyString(publication), is(nullValue()));
        assertThat(fieldWithValueEmptyArray(publication), is(not(nullValue())));
        assertThat(fieldWithValueEmptyArray(publication), is(empty()));
        assertThat(fieldWithValueEmptyMap(publication), is(nullValue()));
    }

    @Test
    public void handlerReturnsNullWhenInputIsAnUniqueIdentifierEntry() {
        PublicationFanoutHandler handler = new PublicationFanoutHandler();
        InputStream inputStream = IoUtils.inputStreamFromResources(
            DYNAMODBEVENT_UNIQUENESS_ENTRY);

        handler.handleRequest(inputStream, outputStream, context);
        DynamoEntryUpdateEvent response = parseResponse();

        assertThat(response.getNewData(), is(nullValue()));
        assertThat(response.getOldData(), is(nullValue()));
    }

    private Pages fieldWithValueEmptyMap(Publication publication) {
        return publication.getEntityDescription().getReference().getPublicationInstance().getPages();
    }

    private List<Contributor> fieldWithValueEmptyArray(Publication publication) {
        return publication.getEntityDescription().getContributors();
    }

    private String fieldWithValueEmptyString(Publication publication) {
        return publication.getEntityDescription().getAbstract();
    }

    private URI fieldWithValueEmptyObject(Publication publication) {
        return publication.getEntityDescription().getReference().getDoi();
    }

    private DynamoEntryUpdateEvent parseResponse() {
        return Try.attempt(() -> objectMapper.readValue(outputStream.toString(),
                                                        DynamoEntryUpdateEvent.class))
            .orElseThrow();
    }
}
