package no.unit.nva.doi.event.producer;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.mock;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.DynamodbEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import no.unit.nva.events.handlers.EventParser;
import no.unit.nva.publication.doi.dto.PublicationHolder;
import nva.commons.utils.IoUtils;
import nva.commons.utils.JsonUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DynamoDbFanoutPublicationDtoProducerTest {

    public static final String EXAMPLE_NAMESPACE = "https://example.net/unittest/namespace/";
    public static final String DOI_PUBLICATION_TYPE = "doi.publication";
    private static final String DYNAMODB_STREAM_EVENT_OLD_AND_NEW_PRESENT_DIFFRENT =
        "dynamodbevent_old_and_new_present_different.json";
    private static final String DYNAMODB_STREAM_EVENT_OLD_AND_NEW_PRESENT_EQUAL =
        "dynamodbevent_old_and_new_present_equal.json";
    private static final String DYNAMODB_STREAM_EVENT_OLD_ONLY = "dynamodbevent_old_only.json";
    private static final Path DYNAMODB_STREAM_EVENT_NEW_ONLY = Path.of("dynamodbevent_new_only.json");
    private static final ObjectMapper objectMapper = JsonUtils.objectMapper;
    private DynamoDbFanoutPublicationDtoProducer handler;
    private Context context;

    /**
     * Setting up test environment.
     */
    @BeforeEach
    public void setUp() {
        handler = new DynamoDbFanoutPublicationDtoProducer(EXAMPLE_NAMESPACE);
        context = mock(Context.class);
    }

    @Test
    void processInputCreatingDtosWhenOnlyNewImageIsPresentInDao() throws JsonProcessingException {
        var eventInputStream = IoUtils.inputStreamFromResources(DYNAMODB_STREAM_EVENT_NEW_ONLY);
        var outputStream = new ByteArrayOutputStream();
        handler.handleRequest(eventInputStream, outputStream, context);
        PublicationHolder actual = outputToPublicationHolder(outputStream);

        assertThat(actual.getType(), is(equalTo(DOI_PUBLICATION_TYPE)));
        assertThat(actual.getItem(), notNullValue());
    }

    @Test
    void processInputSkipsCreatingDtosWhenNoNewImageIsPresentInDao() throws JsonProcessingException {
        var eventFile = IoUtils.stringFromResources(Path.of(DYNAMODB_STREAM_EVENT_OLD_ONLY));
        var event = objectMapper.readValue(eventFile, DynamodbEvent.DynamodbStreamRecord.class);
        var eventBridgeEvent = new EventParser<DynamodbEvent.DynamodbStreamRecord>(
            eventFile).parse(DynamodbEvent.DynamodbStreamRecord.class);
        var actual = handler.processInput(event, eventBridgeEvent, context);

        assertThat(actual, nullValue());
    }

    @Test
    void processInputCreatesDtosWhenOldAndNewImageAreDifferent() throws JsonProcessingException {
        var eventFile = IoUtils.stringFromResources(Path.of(DYNAMODB_STREAM_EVENT_OLD_AND_NEW_PRESENT_DIFFRENT));
        var event = objectMapper.readValue(eventFile, DynamodbEvent.DynamodbStreamRecord.class);
        var eventBridgeEvent = new EventParser<DynamodbEvent.DynamodbStreamRecord>(
            eventFile).parse(DynamodbEvent.DynamodbStreamRecord.class);
        var actual = handler.processInput(event, eventBridgeEvent, context);

        assertThat(actual.getType(), is(equalTo(DOI_PUBLICATION_TYPE)));
        assertThat(actual.getItem(), notNullValue());
    }

    @Test
    void processInputSkipsCreatingDtosWhenOldAndNewImageAreEqual() throws JsonProcessingException {
        var eventFile = IoUtils.stringFromResources(Path.of(DYNAMODB_STREAM_EVENT_OLD_AND_NEW_PRESENT_EQUAL));
        var event = objectMapper.readValue(eventFile, DynamodbEvent.DynamodbStreamRecord.class);
        var eventBridgeEvent = new EventParser<DynamodbEvent.DynamodbStreamRecord>(
            eventFile).parse(DynamodbEvent.DynamodbStreamRecord.class);
        var actual = handler.processInput(event, eventBridgeEvent, context);

        assertThat(actual, nullValue());
    }

    private PublicationHolder outputToPublicationHolder(ByteArrayOutputStream outputStream)
        throws JsonProcessingException {
        String outputString = outputStream.toString();
        PublicationHolder actual = objectMapper.readValue(outputString, PublicationHolder.class);
        return actual;
    }
}