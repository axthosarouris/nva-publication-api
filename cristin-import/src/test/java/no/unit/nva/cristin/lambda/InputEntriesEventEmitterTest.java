package no.unit.nva.cristin.lambda;

import static nva.commons.core.JsonUtils.objectMapperNoEmpty;
import static nva.commons.core.attempt.Try.attempt;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.core.StringContains.containsString;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import com.amazonaws.services.lambda.runtime.Context;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import no.unit.nva.events.models.AwsEventBridgeEvent;
import no.unit.nva.stubs.FakeS3Client;
import no.unit.nva.testutils.IoUtils;
import nva.commons.core.JsonSerializable;
import nva.commons.core.JsonUtils;
import nva.commons.core.attempt.Try;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequestEntry;
import software.amazon.awssdk.services.s3.S3Client;

public class InputEntriesEventEmitterTest {

    public static final String UNEXPECTED_DETAIL_TYPE = "unexpected detail type";
    public static final ImportRequest EXISTING_FILE = new ImportRequest("s3://some/s3/location.file");
    public static final ImportRequest NON_EXISTING_FILE = new ImportRequest("s3://some/s3/nonexisting.file");
    public static final String LINE_SEPARATOR = System.lineSeparator();

    public static final SampleObject[] FILE_01_CONTENTS = randomContents().toArray(SampleObject[]::new);
    public static final Map<String, InputStream> CONTENTS_AS_JSON_ARRAY =
        Map.of(EXISTING_FILE.extractPathFromS3Location(), contentsAsJsonArray());
    public static final Map<String, InputStream> CONTENTS_AS_JSON_OBJECTS_LIST =
        Map.of(EXISTING_FILE.extractPathFromS3Location(), contentsAsJsonObjectsList());

    public static final Context CONTEXT = mock(Context.class);
    public static final String SOME_OTHER_BUS = "someOtherBus";

    private S3Client s3Client;
    private FakeEventBridgeClient eventBridgeClient;
    private InputEntriesEventEmitter handler;
    private ByteArrayOutputStream outputStream;

    @BeforeEach
    public void init() {
        s3Client = new FakeS3Client(CONTENTS_AS_JSON_ARRAY);
        eventBridgeClient = new FakeEventBridgeClient(ApplicationConstants.EVENT_BUS_NAME);
        handler = newHandler();
        outputStream = new ByteArrayOutputStream();
    }

    @Test
    public void handlerThrowsExceptionWhenInputDoesNotHaveTheExpectedDetailType() {
        AwsEventBridgeEvent<ImportRequest> request = new AwsEventBridgeEvent<>();
        request.setDetailType(UNEXPECTED_DETAIL_TYPE);
        request.setDetail(EXISTING_FILE);
        InputStream input = toInputStream(request);

        Executable action = () -> handler.handleRequest(input, outputStream, CONTEXT);
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, action);
        assertThat(exception.getMessage(), containsString(request.getDetailType()));
    }

    @Test
    public void handlerEmitsEventWithResourceWhenFileUriExistsAndContainsDataAsJsonArray() {
        InputStream input = createRequestEventForFile(EXISTING_FILE);

        handler.handleRequest(input, outputStream, CONTEXT);
        List<SampleObject> emittedResourceObjects = collectEmittedObjects();

        assertThat(emittedResourceObjects, containsInAnyOrder(FILE_01_CONTENTS));
    }

    @Test
    public void handlerEmitsEventWithResourceWhenFileUriExistsAndContainsDataAsJsonObjectsList() {
        s3Client = new FakeS3Client(CONTENTS_AS_JSON_OBJECTS_LIST);
        handler = newHandler();

        InputStream input = createRequestEventForFile(EXISTING_FILE);

        handler.handleRequest(input, outputStream, CONTEXT);
        List<SampleObject> emittedResourceObjects = collectEmittedObjects();

        assertThat(emittedResourceObjects, containsInAnyOrder(FILE_01_CONTENTS));
    }

    @Test
    public void handlerThrowsExceptionWhenTryingToEmitToNonExistingEventBus() {
        eventBridgeClient = new FakeEventBridgeClient(SOME_OTHER_BUS);
        handler = newHandler();
        var input = createRequestEventForFile(EXISTING_FILE);
        Executable action = () -> handler.handleRequest(input, outputStream, CONTEXT);
        IllegalStateException exception = assertThrows(IllegalStateException.class, action);
        String eventBusNameUsedByHandler = ApplicationConstants.EVENT_BUS_NAME;
        assertThat(exception.getMessage(), containsString(eventBusNameUsedByHandler));
    }

    @Test
    public void handlerThrowsExceptionWhenInputUriIsNotAnExistingFile() {
        InputStream input = createRequestEventForFile(NON_EXISTING_FILE);
        Executable action = () -> handler.handleRequest(input, outputStream, CONTEXT);
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, action);
        assertThat(exception.getMessage(), containsString(NON_EXISTING_FILE.getS3Location()));
    }

    private static List<SampleObject> randomContents() {
        return Stream.of(SampleObject.random(), SampleObject.random(), SampleObject.random())
                   .collect(Collectors.toList());
    }

    private static InputStream contentsAsJsonArray() {
        List<JsonNode> nodes = contentAsJsonNodes();
        ArrayNode root = JsonUtils.objectMapper.createArrayNode();
        root.addAll(nodes);
        String jsonArrayString = attempt(() -> objectMapperNoEmpty.writeValueAsString(root)).orElseThrow();
        return IoUtils.stringToStream(jsonArrayString);
    }

    private static InputStream contentsAsJsonObjectsList() {
        var objectMapper = objectMapperNoEmpty.configure(SerializationFeature.INDENT_OUTPUT, false);
        String nodesInLines = contentAsJsonNodes()
                                  .stream()
                                  .map(attempt(objectMapper::writeValueAsString))
                                  .map(Try::orElseThrow)
                                  .collect(Collectors.joining(LINE_SEPARATOR));
        return IoUtils.stringToStream(nodesInLines);
    }

    private static List<JsonNode> contentAsJsonNodes() {
        return Stream.of(FILE_01_CONTENTS)

                   .map(JsonSerializable::toJsonString)
                   .map(attempt(objectMapperNoEmpty::readTree))
                   .map(Try::orElseThrow)
                   .collect(Collectors.toList());
    }

    private InputStream createRequestEventForFile(ImportRequest detail) {
        AwsEventBridgeEvent<ImportRequest> request = new AwsEventBridgeEvent<>();
        request.setDetailType(FilenameEventEmitter.EVENT_DETAIL_TYPE);
        request.setDetail(detail);
        return toInputStream(request);
    }

    private InputEntriesEventEmitter newHandler() {
        return new InputEntriesEventEmitter(s3Client, eventBridgeClient);
    }

    private List<SampleObject> collectEmittedObjects() {
        return eventBridgeClient.getEvenRequests()
                   .stream()
                   .flatMap(e -> e.entries().stream())
                   .map(PutEventsRequestEntry::detail)
                   .map(SampleObject::fromJson)
                   .collect(Collectors.toList());
    }

    private InputStream toInputStream(AwsEventBridgeEvent<ImportRequest> request) {
        return attempt(() -> objectMapperNoEmpty.writeValueAsString(request))
                   .map(IoUtils::stringToStream)
                   .orElseThrow();
    }
}