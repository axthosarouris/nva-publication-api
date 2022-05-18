package no.unit.nva.publication.events.handlers.dynamodbstream;

import static no.unit.nva.publication.events.handlers.PublicationEventsConfig.EVENTS_BUCKET;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import com.amazonaws.services.lambda.runtime.events.DynamodbEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import no.unit.nva.commons.json.JsonUtils;
import no.unit.nva.events.models.EventReference;
import no.unit.nva.s3.S3Driver;
import no.unit.nva.stubs.FakeContext;
import no.unit.nva.stubs.FakeEventBridgeClient;
import no.unit.nva.stubs.FakeS3Client;
import nva.commons.core.SingletonCollector;
import nva.commons.core.ioutils.IoUtils;
import nva.commons.core.paths.UnixPath;
import nva.commons.core.paths.UriWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequestEntry;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

class DynamodbStreamToEventBridgeHandlerTest {

    public static final String DYNAMODB_STREAM_EVENT =
        IoUtils.stringFromResources(Path.of("dynamodbstreams/event.json"));
    private final ObjectMapper objectMapper = new ObjectMapper();
    private EventPublisher eventPublisher;
    private FakeS3Client s3Client;
    private FakeContext context;
    private DynamodbStreamToEventBridgeHandler handler;
    private FakeEventBridgeClient eventBridgeClient;

    @BeforeEach
    public void init() {
        this.s3Client = new FakeS3Client();
        createFailingS3Client();
        this.eventBridgeClient = new FakeEventBridgeClient();
        this.context = new FakeContext() {
            @Override
            public String getInvokedFunctionArn() {
                return randomString();
            }
        };
        this.eventPublisher = publisherThatPrintsToConsole();
        this.handler = new DynamodbStreamToEventBridgeHandler(s3Client, eventBridgeClient, eventPublisher);
    }

    //TODO this pre-existing test does nothng. Should be deleted after the transformation
    @Test
    void handleRequestWritesToConsoleOnValidEvent() throws Exception {
        var event = objectMapper.readValue(DYNAMODB_STREAM_EVENT, DynamodbEvent.class);
        handler.handleRequest(event, context);
    }

    @Test
    void shouldWriteDynamoDbStreamEventInS3Bucket() throws JsonProcessingException {
        var event = parseDynamoDbEvent(DYNAMODB_STREAM_EVENT);
        var savedFile = handler.handleRequest(event, context);
        var savedEvent = readEventSavedInS3(savedFile.getUri());
        var expectedEventId = event.getRecords().stream().collect(SingletonCollector.collect()).getEventID();
        var actualEventId = savedEvent.getRecords().stream().collect(SingletonCollector.collect()).getEventID();
        assertThat(actualEventId, is(equalTo(expectedEventId)));
    }

    @Test
    void shouldEmitEventWithContainingTheEventFileUriAndTheEventTopic() throws JsonProcessingException {
        var event = parseDynamoDbEvent(DYNAMODB_STREAM_EVENT);
        handler.handleRequest(event, context);
        var s3Driver = new S3Driver(s3Client, EVENTS_BUCKET);
        var savedFileUri = fetchSavedFileUri(s3Driver);
        var publishedUri = eventBridgeClient.getRequestEntries().stream()
            .map(PutEventsRequestEntry::detail)
            .map(EventReference::fromJson)
            .map(EventReference::getUri)
            .collect(SingletonCollector.collect());
        assertThat(publishedUri, is(equalTo(savedFileUri)));
    }

    @Test
    void shouldReturnEventContainingTheEventFileUriAndTheEventTopicForUsageWithLambdaDestinations()
        throws JsonProcessingException {
        var event = parseDynamoDbEvent(DYNAMODB_STREAM_EVENT);
        var lambdaDestinationsEvent=handler.handleRequest(event, context);
        var s3Driver = new S3Driver(s3Client, EVENTS_BUCKET);
        var savedFileUri = fetchSavedFileUri(s3Driver);
        var publishedUri = lambdaDestinationsEvent.getUri();
        assertThat(publishedUri, is(equalTo(savedFileUri)));
    }

    private URI fetchSavedFileUri(S3Driver s3Driver) {
        return s3Driver.listAllFiles(UnixPath.ROOT_PATH).stream()
            .map(path -> UriWrapper.fromUri("s3://" + EVENTS_BUCKET).addChild(path))
            .map(UriWrapper::getUri)
            .collect(SingletonCollector.collect());
    }

    @Test
    void shouldNotFailAndBreakCurrentFunctionalityWhenFailingToSaveInS3ForFutureFunctionality()
        throws JsonProcessingException {
        this.handler = new DynamodbStreamToEventBridgeHandler(createFailingS3Client(),
                                                              eventBridgeClient,
                                                              eventPublisher);
        var event = parseDynamoDbEvent(DYNAMODB_STREAM_EVENT);
        assertDoesNotThrow(() -> handler.handleRequest(event, context));
    }

    private FakeS3Client createFailingS3Client() {
        return new FakeS3Client() {
            @SuppressWarnings("PMD.CloseResource")
            @Override
            public PutObjectResponse putObject(PutObjectRequest putObjectRequest, RequestBody requestBody) {
                throw new RuntimeException();
            }
        };
    }

    private DynamodbEvent readEventSavedInS3(URI savedFile) throws JsonProcessingException {
        var s3Driver = new S3Driver(s3Client, EVENTS_BUCKET);
        var savedContent = s3Driver.getFile(UriWrapper.fromUri(savedFile).toS3bucketPath());
        return parseDynamoDbEvent(savedContent);
    }

    private DynamodbEvent parseDynamoDbEvent(String savedContent) throws JsonProcessingException {
        return JsonUtils.dtoObjectMapper.readValue(savedContent, DynamodbEvent.class);
    }

    private EventPublisher publisherThatPrintsToConsole() {
        return event -> {
            try {
                new ObjectMapper().writeValue(System.out, event);
            } catch (IOException e) {
                e.printStackTrace();
            }
        };
    }
}
