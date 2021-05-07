package no.unit.nva.publication.s3imports;

import static no.unit.nva.publication.PublicationGenerator.randomString;
import static nva.commons.core.JsonUtils.objectMapperNoEmpty;
import static nva.commons.core.attempt.Try.attempt;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.StringContains.containsString;
import static org.junit.jupiter.api.Assertions.assertThrows;
import com.amazonaws.services.lambda.runtime.Context;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequestEntry;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.ion.IonReader;
import software.amazon.ion.IonWriter;
import software.amazon.ion.system.IonReaderBuilder;
import software.amazon.ion.system.IonTextWriterBuilder;

public class FileEntriesEventEmitterTest {

    public static final String UNEXPECTED_DETAIL_TYPE = "unexpected detail type";

    public static final String SOME_USER = randomString();
    public static final String IMPORT_EVENT_TYPE = "importEventType";
    public static final ImportRequest IMPORT_REQUEST_FOR_EXISTING_FILE =
        newStandardImportRequest("s3://some/s3/folder/location.file");
    public static final ImportRequest IMPORT_REQUEST_FOR_NON_EXISTING_FILE = newStandardImportRequest(
        "s3://some/s3/nonexisting.file");
    public static final String LINE_SEPARATOR = System.lineSeparator();
    public static final SampleObject[] FILE_01_CONTENTS = randomObjects().toArray(SampleObject[]::new);
    public static final Context CONTEXT = Mockito.mock(Context.class);
    public static final String SOME_OTHER_BUS = "someOtherBus";
    private S3Client s3Client;
    private FakeEventBridgeClient eventBridgeClient;
    private FileEntriesEventEmitter handler;
    private ByteArrayOutputStream outputStream;

    @BeforeEach
    public void init() {
        s3Client = new FakeS3Client(fileWithContentsAsJsonArray().toMap());
        eventBridgeClient = new FakeEventBridgeClient(ApplicationConstants.EVENT_BUS_NAME);
        handler = newHandler();
        outputStream = new ByteArrayOutputStream();
    }

    @Test
    public void handlerEmitsEventWithResourceWhenFileUriExistsAndContainsDataAsJsonArray() {
        InputStream input = createRequestEventForFile(IMPORT_REQUEST_FOR_EXISTING_FILE);
        FileEntriesEventEmitter handler = newHandler();

        handler.handleRequest(input, outputStream, CONTEXT);
        List<SampleObject> emittedResourceObjects = collectEmittedObjects(eventBridgeClient);

        assertThat(emittedResourceObjects, containsInAnyOrder(FILE_01_CONTENTS));
    }

    @Test
    public void handlerThrowsExceptionWhenInputDoesNotHaveTheExpectedDetailType() {
        AwsEventBridgeEvent<ImportRequest> request = new AwsEventBridgeEvent<>();
        request.setDetailType(UNEXPECTED_DETAIL_TYPE);
        request.setDetail(IMPORT_REQUEST_FOR_EXISTING_FILE);
        InputStream input = toInputStream(request);

        Executable action = () -> handler.handleRequest(input, outputStream, CONTEXT);
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, action);
        assertThat(exception.getMessage(), containsString(request.getDetailType()));
    }

    @Test
    public void handlerEmitsEventWithResourceWhenFileUriExistsAndContainsDataAsJsonObjectsList() {
        s3Client = new FakeS3Client(fileWithContentsAsJsonObjectsLists().toMap());
        handler = newHandler();
        InputStream input = createRequestEventForFile(IMPORT_REQUEST_FOR_EXISTING_FILE);

        handler.handleRequest(input, outputStream, CONTEXT);
        List<SampleObject> emittedResourceObjects = collectEmittedObjects(eventBridgeClient);

        assertThat(emittedResourceObjects, containsInAnyOrder(FILE_01_CONTENTS));
    }

    @Test
    public void handlerThrowsExceptionWhenTryingToEmitToNonExistingEventBus() {
        eventBridgeClient = new FakeEventBridgeClient(SOME_OTHER_BUS);
        handler = newHandler();
        var input = createRequestEventForFile(IMPORT_REQUEST_FOR_EXISTING_FILE);
        Executable action = () -> handler.handleRequest(input, outputStream, CONTEXT);
        IllegalStateException exception = assertThrows(IllegalStateException.class, action);
        String eventBusNameUsedByHandler = ApplicationConstants.EVENT_BUS_NAME;
        assertThat(exception.getMessage(), containsString(eventBusNameUsedByHandler));
    }

    @Test
    public void handlerThrowsExceptionWhenInputUriIsNotAnExistingFile() {
        InputStream input = createRequestEventForFile(IMPORT_REQUEST_FOR_NON_EXISTING_FILE);
        Executable action = () -> handler.handleRequest(input, outputStream, CONTEXT);
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, action);
        assertThat(exception.getMessage(), containsString(IMPORT_REQUEST_FOR_NON_EXISTING_FILE.getS3Location()));
    }

    @Test
    public void handlerEmitsEventsWithImportRequestsThatIncludeInputPublicationsOwner() {
        InputStream input = createRequestEventForFile(IMPORT_REQUEST_FOR_EXISTING_FILE);
        handler.handleRequest(input, outputStream, CONTEXT);
        List<String> publicationOwners = extractPublicationOwnersFromGeneratedEvents();
        assertThat(publicationOwners.size(), is(equalTo(FILE_01_CONTENTS.length)));
        for (String publicationOwner : publicationOwners) {
            assertThat(publicationOwner, is(equalTo(SOME_USER)));
        }
    }

    @Test
    public void handlerEmitsEventsWithDetailTypeEqualToInputsImportRequestEventType() {
        String expectedImportRequestEventType = randomString();
        ImportRequest importRequestWithCustomType =
            new ImportRequest(IMPORT_REQUEST_FOR_EXISTING_FILE.getS3Location(),
                              IMPORT_REQUEST_FOR_EXISTING_FILE.getPublicationsOwner(),
                              expectedImportRequestEventType);
        InputStream input = createRequestEventForFile(importRequestWithCustomType);
        handler.handleRequest(input, outputStream, CONTEXT);
        var detailTypes = eventBridgeClient
                              .getEvenRequests()
                              .stream()
                              .flatMap(eventRequest -> eventRequest.entries().stream())
                              .map(PutEventsRequestEntry::detailType)
                              .collect(Collectors.toList());
        assertThat(detailTypes.size(), is(equalTo(FILE_01_CONTENTS.length)));
        for (String detailType : detailTypes) {
            assertThat(detailType, is(equalTo(expectedImportRequestEventType)));
        }
    }

    @ParameterizedTest
    @MethodSource("ionContentProvider")
    public void handlerEmitsEventsWithJsonFormatWhenInputIsFileWithIonContent(
        Function<Collection<SampleObject>, FileContent> ionContentProvider) {
        List<SampleObject> sampleObjects = randomObjects();
        s3Client = new FakeS3Client(ionContentProvider.apply(sampleObjects).toMap());
        InputStream input = createRequestEventForFile(IMPORT_REQUEST_FOR_EXISTING_FILE);
        handler = newHandler();
        handler.handleRequest(input, outputStream, CONTEXT);
        List<SampleObject> emittedObjects = collectEmittedObjects(eventBridgeClient);
        assertThat(emittedObjects, containsInAnyOrder(sampleObjects.toArray(SampleObject[]::new)));
    }

    private static Stream<Function<Collection<SampleObject>, FileContent>> ionContentProvider() {
        return Stream.of(
            FileEntriesEventEmitterTest::fileWithContentAsIonObjectsList,
            FileEntriesEventEmitterTest::fileWithContentAsIonArray

        );
    }

    private static ImportRequest newStandardImportRequest(String s3location) {
        return new ImportRequest(s3location, SOME_USER, IMPORT_EVENT_TYPE);
    }

    private static FileContent fileWithContentsAsJsonObjectsLists() {
        return new FileContent(IMPORT_REQUEST_FOR_EXISTING_FILE.extractPathFromS3Location(),
                               contentsAsJsonObjectsList());
    }

    private static FileContent fileWithContentsAsJsonArray() {
        return new FileContent(IMPORT_REQUEST_FOR_EXISTING_FILE.extractPathFromS3Location(), contentsAsJsonArray());
    }

    private static FileContent fileWithContentAsIonObjectsList(Collection<SampleObject> sampleObjects) {
        String ionObjectsList = createNewIonObjectsList(sampleObjects);
        //verify that this is not a list of json objects.
        assertThrows(Exception.class, () -> objectMapperNoEmpty.readTree(ionObjectsList));
        return new FileContent(IMPORT_REQUEST_FOR_EXISTING_FILE.extractPathFromS3Location(),
                               IoUtils.stringToStream(ionObjectsList));
    }

    private static FileContent fileWithContentAsIonArray(Collection<SampleObject> sampleObjects) {
        String ionArray = attempt(() -> createNewIonArray(sampleObjects)).orElseThrow();
        return new FileContent(IMPORT_REQUEST_FOR_EXISTING_FILE.extractPathFromS3Location(),
                               IoUtils.stringToStream(ionArray));
    }

    private static InputStream contentsAsJsonArray() {
        List<JsonNode> nodes = contentAsJsonNodes();
        ArrayNode root = JsonUtils.objectMapperNoEmpty.createArrayNode();
        root.addAll(nodes);
        String jsonArrayString = attempt(() -> objectMapperNoEmpty.writeValueAsString(root)).orElseThrow();
        return IoUtils.stringToStream(jsonArrayString);
    }

    private static InputStream contentsAsJsonObjectsList() {
        ObjectMapper objectMapperWithoutLineBreaks =
            objectMapperNoEmpty.configure(SerializationFeature.INDENT_OUTPUT, false);
        String nodesInLines = contentAsJsonNodes()
                                  .stream()
                                  .map(attempt(objectMapperWithoutLineBreaks::writeValueAsString))
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

    private static String createNewIonObjectsList(Collection<SampleObject> sampleObjects) {
        return sampleObjects.stream()
                   .map(attempt(objectMapperNoEmpty::writeValueAsString))
                   .map(attempt -> attempt.map(FileEntriesEventEmitterTest::jsonToIon))
                   .map(Try::orElseThrow)
                   .collect(Collectors.joining(System.lineSeparator()));
    }

    private static String createNewIonArray(Collection<SampleObject> sampleObjects) throws IOException {
        String jsonString = objectMapperNoEmpty.writeValueAsString(sampleObjects);
        return jsonToIon(jsonString);
    }

    private static String jsonToIon(String jsonString) throws IOException {
        IonReader reader = IonReaderBuilder.standard().build(jsonString);
        StringBuilder stringAppender = new StringBuilder();
        IonWriter writer = IonTextWriterBuilder.standard().build(stringAppender);
        writer.writeValues(reader);
        return stringAppender.toString();
    }

    private static List<SampleObject> randomObjects() {
        return Stream.of(SampleObject.random(), SampleObject.random(), SampleObject.random())
                   .collect(Collectors.toList());
    }

    private FileEntriesEventEmitter newHandler() {
        return new FileEntriesEventEmitter(s3Client, eventBridgeClient);
    }

    private List<String> extractPublicationOwnersFromGeneratedEvents() {
        return eventBridgeClient.getEvenRequests().stream()
                   .flatMap(eventsRequest -> eventsRequest.entries().stream())
                   .map(PutEventsRequestEntry::detail)
                   .map(attempt(detailString -> objectMapperNoEmpty.readValue(detailString, FileContentsEvent.class)))
                   .map(Try::orElseThrow)
                   .map(FileContentsEvent::getPublicationsOwner)
                   .collect(Collectors.toList());
    }

    private InputStream createRequestEventForFile(ImportRequest detail) {
        AwsEventBridgeEvent<ImportRequest> request = new AwsEventBridgeEvent<>();
        request.setDetailType(FilenameEventEmitter.EVENT_DETAIL_TYPE);
        request.setDetail(detail);
        return toInputStream(request);
    }

    private List<SampleObject> collectEmittedObjects(FakeEventBridgeClient eventBridgeClient) {
        return eventBridgeClient.getEvenRequests()
                   .stream()
                   .flatMap(e -> e.entries().stream())
                   .map(PutEventsRequestEntry::detail)
                   .map(detail -> FileContentsEvent.fromJson(detail, SampleObject.class))
                   .map(FileContentsEvent::getContents)
                   .collect(Collectors.toList());
    }

    private InputStream toInputStream(AwsEventBridgeEvent<ImportRequest> request) {
        return attempt(() -> objectMapperNoEmpty.writeValueAsString(request))
                   .map(IoUtils::stringToStream)
                   .orElseThrow();
    }
}