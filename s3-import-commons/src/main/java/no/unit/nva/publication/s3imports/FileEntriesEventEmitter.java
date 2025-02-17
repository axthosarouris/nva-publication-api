package no.unit.nva.publication.s3imports;

import static no.unit.nva.publication.s3imports.ApplicationConstants.EMPTY_STRING;
import static no.unit.nva.publication.s3imports.ApplicationConstants.ERRORS_FOLDER;
import static no.unit.nva.publication.s3imports.ApplicationConstants.EVENTS_BUCKET;
import static no.unit.nva.publication.s3imports.ApplicationConstants.defaultEventBridgeClient;
import static no.unit.nva.publication.s3imports.ApplicationConstants.defaultS3Client;
import static no.unit.nva.publication.s3imports.FileImportUtils.timestampToString;
import static no.unit.nva.publication.s3imports.S3ImportsConfig.s3ImportsMapper;
import static nva.commons.core.attempt.Try.attempt;
import static nva.commons.core.exceptions.ExceptionUtils.stackTraceInSingleLine;
import com.amazonaws.services.lambda.runtime.Context;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import no.unit.nva.events.handlers.EventHandler;
import no.unit.nva.events.models.AwsEventBridgeEvent;
import no.unit.nva.events.models.EventReference;
import no.unit.nva.s3.S3Driver;
import nva.commons.core.JacocoGenerated;
import nva.commons.core.StringUtils;
import nva.commons.core.attempt.Try;
import nva.commons.core.paths.UnixPath;
import nva.commons.core.paths.UriWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.eventbridge.EventBridgeClient;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequest;
import software.amazon.awssdk.services.eventbridge.model.PutEventsResponse;
import software.amazon.awssdk.services.eventbridge.model.PutEventsResultEntry;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

/**
 * The body of the event (field "detail") is of type {@link FileContentsEvent} and it contains the data of the file
 * located in the s3Location defined in {@link EventReference#getUri()} ()}.
 *
 * <p>In its present form the {@link FileContentsEvent} contains also a field with the name "publicationsOwner" which
 * is specific to the task of importing Cristin records.  In the future, this should be replaced by a more generic
 * format such as a {@link Map} annotated with "@JsonAnySetter".
 */
@JacocoGenerated
public class FileEntriesEventEmitter extends EventHandler<EventReference, String> {
    
    public static final String WRONG_TOPIC_ERROR = "event does not contain the correct topic:";
    public static final String FILE_NOT_FOUND_ERROR = "File not found: ";
    public static final String FILE_EXTENSION_ERROR = ".error";
    public static final String PARTIAL_FAILURE = "PartialFailure";
    public static final PutEventsRequest NO_REQUEST_WAS_EMITTED = null;
    public static final String FILE_CONTENTS_EMISSION_EVENT_TOPIC = "PublicationService.DataImport.DataEntry";
    public static final int NUMBER_OF_EMITTED_ENTRIES_PER_BATCH = 100;
    public static final String EXPECTED_INPUT_TOPIC = FilenameEventEmitter.FILENAME_EMISSION_EVENT_TOPIC;
    private static final String CANONICAL_NAME = FileEntriesEventEmitter.class.getCanonicalName();
    private static final String LINE_SEPARATOR = System.lineSeparator();
    private static final boolean SEQUENTIAL = false;
    private static final Logger logger = LoggerFactory.getLogger(FileEntriesEventEmitter.class);
    private static final String NON_EMITTED_ENTRIES_WARNING_PREFIX = "Some entries failed to be emitted: ";
    private static final String EXCEPTION_STACKTRACE_MESSAGE_TEMPLATE =
        "File in location: %s Failed with the following exception: %s";
    private static final String CONSECUTIVE_JSON_OBJECTS = "}\\s*\\{";
    private static final String NODES_IN_ARRAY = "},{";
    private static final Object END_OF_ARRAY = "]";
    private static final String BEGINNING_OF_ARRAY = "[";
    private final S3Client s3Client;
    private final EventBridgeClient eventBridgeClient;
    
    @JacocoGenerated
    public FileEntriesEventEmitter() {
        this(defaultS3Client(), defaultEventBridgeClient());
    }
    
    public FileEntriesEventEmitter(S3Client s3Client,
                                   EventBridgeClient eventBridgeClient) {
        super(EventReference.class);
        this.s3Client = s3Client;
        this.eventBridgeClient = eventBridgeClient;
    }
    
    @Override
    protected String processInput(EventReference input, AwsEventBridgeEvent<EventReference> event, Context context) {
        validateEvent(event);
        var failedEntries = attemptToEmitEvents(input, context);
        if (thereAreFailures(failedEntries)) {
            storeErrorReportsInS3(failedEntries, input);
            logWarningForNotEmittedEntries(failedEntries);
        }
        
        return returnNothingOrThrowExceptionWhenEmissionFailedCompletely(failedEntries);
    }
    
    private boolean thereAreFailures(Try<List<PutEventsResult>> failedEntries) {
        return completeEmissionFailure(failedEntries) || partialEmissionFailure(failedEntries);
    }
    
    private boolean partialEmissionFailure(Try<List<PutEventsResult>> failedEntries) {
        return failedEntries.isSuccess() && !failedEntries.orElseThrow().isEmpty();
    }
    
    private boolean completeEmissionFailure(Try<List<PutEventsResult>> failedEntries) {
        return failedEntries.isFailure();
    }
    
    private Try<List<PutEventsResult>> attemptToEmitEvents(EventReference input, Context context) {
        var s3Driver = new S3Driver(s3Client, input.extractBucketName());
        return attempt(() -> fetchFileFromS3(input, s3Driver))
                   .map(this::parseContents)
                   .map(jsonNodes -> generateEventBodies(input, jsonNodes))
                   .map(eventBodies -> emitEvents(context, eventBodies));
    }
    
    private String returnNothingOrThrowExceptionWhenEmissionFailedCompletely(
        Try<List<PutEventsResult>> emitEventsAttempt) {
        return emitEventsAttempt.map(attempt -> EMPTY_STRING).orElseThrow();
    }
    
    private void storeErrorReportsInS3(Try<List<PutEventsResult>> failedEntries, EventReference input) {
        S3Driver s3Driver = new S3Driver(s3Client, input.extractBucketName());
        var reportFilename = generateErrorReportUri(input, failedEntries);
        var putEventsResults =
            failedEntries.orElse(fails -> generateReportIndicatingTotalEmissionFailure(fails, input.getUri()));
        
        if (!putEventsResults.isEmpty()) {
            String reportContent = PutEventsResult.toString(putEventsResults);
            attempt(() -> s3Driver.insertFile(reportFilename.toS3bucketPath(), reportContent)).orElseThrow();
        }
    }
    
    private UriWrapper generateErrorReportUri(EventReference input, Try<List<PutEventsResult>> failedEntries) {
        var inputUri = UriWrapper.fromUri(input.getUri().toString());
        var bucket = inputUri.getHost();
        
        var errorType = failedEntries.isSuccess()
                            ? PARTIAL_FAILURE
                            : failedEntries.getException().getClass().getSimpleName();
        return bucket
                   .addChild(ERRORS_FOLDER)
                   .addChild(timestampToString(input.getTimestamp()))
                   .addChild(errorType)
                   .addChild(inputUri.getParent().map(UriWrapper::getPath).orElse(UnixPath.EMPTY_PATH))
                   .addChild(makeFileExtensionError(inputUri.getLastPathElement()));
    }
    
    private String makeFileExtensionError(String filename) {
        return filename + FILE_EXTENSION_ERROR;
    }
    
    private Stream<FileContentsEvent<JsonNode>> generateEventBodies(EventReference input, List<JsonNode> contents) {
        var fileUri = input.getUri();
        var timestamp = input.getTimestamp();
        return contents.stream()
                   .map(json -> new FileContentsEvent<>(
                       FILE_CONTENTS_EMISSION_EVENT_TOPIC,
                       input.getSubtopic(),
                       fileUri,
                       timestamp,
                       json));
    }
    
    private List<PutEventsResult> emitEvents(Context context,
                                             Stream<FileContentsEvent<JsonNode>> eventBodies) {
        List<EventReference> eventReferences = createEventReferences(eventBodies);
        return emitEventReferences(context, eventReferences);
    }
    
    private List<PutEventsResult> emitEventReferences(Context context, List<EventReference> eventReferences) {
        var batchEventEmitter =
            new BatchEventEmitter<EventReference>(CANONICAL_NAME,
                context.getInvokedFunctionArn(),
                eventBridgeClient);
        batchEventEmitter.addEvents(eventReferences);
        return batchEventEmitter.emitEvents(NUMBER_OF_EMITTED_ENTRIES_PER_BATCH);
    }
    
    private List<EventReference> createEventReferences(Stream<FileContentsEvent<JsonNode>> eventBodies) {
        var s3Driver = new S3Driver(s3Client, EVENTS_BUCKET);
        return eventBodies
                   .map(attempt(fileContents -> fileContents.toEventReference(s3Driver)))
                   .map(Try::orElseThrow)
                   .collect(Collectors.toList());
    }
    
    private List<PutEventsResult> generateReportIndicatingTotalEmissionFailure(
        Try<List<PutEventsResult>> completeEmissionFailure, URI s3Location) {
        PutEventsResponse customPutEventsResponse =
            generatePutEventsResultIndicatingNoEventsWereEmitted(completeEmissionFailure, s3Location);
        PutEventsResult putEventsResult = new PutEventsResult(NO_REQUEST_WAS_EMITTED, customPutEventsResponse);
        return Collections.singletonList(putEventsResult);
    }
    
    private PutEventsResponse generatePutEventsResultIndicatingNoEventsWereEmitted(
        Try<List<PutEventsResult>> completeEmissionFailure, URI s3Location) {
        String fileLocationAndExceptionStackTraceAsResultMessage =
            String.format(EXCEPTION_STACKTRACE_MESSAGE_TEMPLATE, s3Location,
                stackTraceInSingleLine(completeEmissionFailure.getException()));
        PutEventsResultEntry putEventsResultEntry =
            PutEventsResultEntry.builder().errorMessage(fileLocationAndExceptionStackTraceAsResultMessage)
                .build();
        return PutEventsResponse.builder().entries(putEventsResultEntry).build();
    }
    
    private String fetchFileFromS3(EventReference input, S3Driver s3Driver) {
        try {
            return s3Driver.readEvent(input.getUri());
        } catch (NoSuchKeyException exception) {
            throw new IllegalArgumentException(FILE_NOT_FOUND_ERROR + input.getUri(), exception);
        }
    }
    
    private void validateEvent(AwsEventBridgeEvent<EventReference> event) {
        if (!EXPECTED_INPUT_TOPIC.equalsIgnoreCase(event.getDetail().getTopic())) {
            logger.info(event.toJsonString());
            throw new IllegalArgumentException(WRONG_TOPIC_ERROR + event.getDetail().getTopic());
        }
    }
    
    private void logWarningForNotEmittedEntries(Try<List<PutEventsResult>> failedRequests) {
        String failedRequestsString = failedRequests
                                          .stream()
                                          .map(PutEventsResult::toString)
                                          .collect(Collectors.joining(LINE_SEPARATOR));
        if (StringUtils.isNotBlank(failedRequestsString)) {
            logger.warn(NON_EMITTED_ENTRIES_WARNING_PREFIX + failedRequestsString);
        }
    }
    
    private List<JsonNode> parseContents(String content) {
        Try<List<JsonNode>> result = attempt(() -> parseContentAsJsonArray(content));
        if (result.isFailure()) {
            result = attempt(() -> parseContentsAsIndependentConsecutiveJsonObjects(content));
        }
        if (result.isFailure()) {
            result = attempt(() -> parseContentsAsIonFormat(content));
        }
        return result.orElseThrow();
    }
    
    private List<JsonNode> parseContentsAsIonFormat(String content) {
        return S3IonReader.extractJsonNodesFromIonContent(content).collect(Collectors.toList());
    }
    
    private List<JsonNode> parseContentsAsIndependentConsecutiveJsonObjects(String content) {
        
        return attempt(() -> content.replaceAll(CONSECUTIVE_JSON_OBJECTS, NODES_IN_ARRAY))
                   .map(jsonObjectStrings -> BEGINNING_OF_ARRAY + jsonObjectStrings + END_OF_ARRAY)
                   .map(jsonArrayString -> (ArrayNode) s3ImportsMapper.readTree(jsonArrayString))
                   .map(array -> toStream(array).collect(Collectors.toList()))
                   .orElseThrow();
    }
    
    private Stream<JsonNode> toStream(ArrayNode root) {
        return StreamSupport
                   .stream(Spliterators.spliteratorUnknownSize(root.elements(), Spliterator.ORDERED), SEQUENTIAL);
    }
    
    private List<JsonNode> parseContentAsJsonArray(String content) throws JsonProcessingException {
        return toStream(parseAsArrayNode(content)).collect(Collectors.toList());
    }
    
    private ArrayNode parseAsArrayNode(String content) throws JsonProcessingException {
        JsonNode jsonNode = s3ImportsMapper.readTree(content);
        if (jsonNode.isArray()) {
            return (ArrayNode) jsonNode;
        } else {
            throw new IllegalArgumentException("Content is not array node");
        }
    }
}
