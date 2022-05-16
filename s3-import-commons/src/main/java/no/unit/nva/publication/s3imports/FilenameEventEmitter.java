package no.unit.nva.publication.s3imports;

import static java.util.Objects.isNull;
import static no.unit.nva.publication.s3imports.ApplicationConstants.ERRORS_FOLDER;
import static no.unit.nva.publication.s3imports.ApplicationConstants.defaultClock;
import static no.unit.nva.publication.s3imports.ApplicationConstants.defaultEventBridgeClient;
import static no.unit.nva.publication.s3imports.ApplicationConstants.defaultS3Client;
import static no.unit.nva.publication.s3imports.FileImportUtils.timestampToString;
import static no.unit.nva.publication.s3imports.S3ImportsConfig.s3ImportsMapper;
import static nva.commons.core.attempt.Try.attempt;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestStreamHandler;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import no.unit.nva.s3.S3Driver;
import nva.commons.core.Environment;
import nva.commons.core.JacocoGenerated;
import nva.commons.core.StringUtils;
import nva.commons.core.attempt.Try;
import nva.commons.core.ioutils.IoUtils;
import nva.commons.core.paths.UnixPath;
import nva.commons.core.paths.UriWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.eventbridge.EventBridgeClient;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequest;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequestEntry;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * {@link FilenameEventEmitter} accepts an {@link ImportRequest}, it lists all the files in the S3 location defined in
 * the {@link ImportRequest} and it emits on event per filename.
 *
 * <p>Each event has as event detail-type the value {@link FilenameEventEmitter#FILENAME_EMISSION_EVENT_TOPIC} and
 * detail
 * (event-body) an {@link ImportRequest} where s3Location is the URI of the respective file and the rest of the fields
 * are copied from the input.
 */
public class FilenameEventEmitter implements RequestStreamHandler {

    public static final String FILENAME_EMISSION_EVENT_TOPIC = "PublicationService.DataImport.Filename";

    public static final Environment ENVIRONMENT = new Environment();
    public static final String EXPECTED_BODY_MESSAGE =
        "The expected json body contains only an s3Location.\nThe received body was: ";
    public static final String WRONG_OR_EMPTY_S3_LOCATION_ERROR = "S3 location does not exist or is empty:";

    public static final String INFORM_USER_THAT_EVENT_TYPE_IS_SET_IN_ENV =
        "Import event subtopic is set for this handler and cannot be set by the user. "
        + EXPECTED_BODY_MESSAGE;

    public static final String LINE_SEPARATOR = System.lineSeparator();
    public static final String NON_EMITTED_FILENAMES_WARNING_PREFIX = "Some files failed to be emitted:";
    public static final String RUNNING_CLASS_NAME = FilenameEventEmitter.class.getCanonicalName();
    public static final String ERROR_REPORT_FILENAME = Instant.now().toString() + "emitFilenamesReport.error.";

    public static final String FILENAME_EMISSION_EVENT_SUBTOPIC =
        ENVIRONMENT.readEnv("FILENAME_EMISSION_EVENT_SUBTOPIC");
    public static final int NUMBER_OF_EMITTED_FILENAMES_PER_BATCH = 10;
    private static final Logger logger = LoggerFactory.getLogger(FilenameEventEmitter.class);
    private final S3Client s3Client;
    private final EventBridgeClient eventBridgeClient;
    private final Clock clock;
    private Instant timestamp;

    @JacocoGenerated
    public FilenameEventEmitter() {
        this(defaultS3Client(), defaultEventBridgeClient(), defaultClock());
    }

    public FilenameEventEmitter(S3Client s3Client, EventBridgeClient eventBridgeClient, Clock clock) {
        this.s3Client = s3Client;
        this.eventBridgeClient = eventBridgeClient;
        this.clock = clock;
    }

    @Override
    public void handleRequest(InputStream input, OutputStream output, Context context) throws IOException {
        timestamp = clock.instant();
        ImportRequest importRequest = parseInput(input);
        List<URI> files = listFiles(importRequest);
        validateImportRequest(importRequest, files);
        List<PutEventsResult> failedRequests = emitEvents(context, files);
        logWarningForNotEmittedFilenames(failedRequests);
        List<URI> notEmittedFilenames = collectNotEmittedFilenames(failedRequests);
        writeFailedEmitActionsInS3(failedRequests, importRequest);
        writeOutput(output, notEmittedFilenames);
    }

    private void writeFailedEmitActionsInS3(List<PutEventsResult> failedRequests, ImportRequest request)
        throws IOException {
        UriWrapper errorReportUri = createErrorReportUri(request);
        S3Driver s3Driver = new S3Driver(s3Client, request.extractBucketFromS3Location());
        String errorReportContent = PutEventsResult.toString(failedRequests);
        if (!failedRequests.isEmpty()) {
            s3Driver.insertFile(errorReportUri.toS3bucketPath(), errorReportContent);
        }
    }

    private UriWrapper createErrorReportUri(ImportRequest request) {
        UriWrapper inputFolderUri =  UriWrapper.fromUri(request.getS3Location());
        UriWrapper bucketUri = inputFolderUri.getHost();
        return bucketUri
            .addChild(ERRORS_FOLDER)
            .addChild(timestampToString(timestamp))
            .addChild(inputFolderUri.getPath())
            .addChild(ERROR_REPORT_FILENAME);
    }

    private URI createUri(URI s3Location, UnixPath filename) {
        return Try.of(s3Location)
            .map(UriWrapper::fromUri)
            .map(UriWrapper::getHost)
            .map(uri -> uri.addChild(filename))
            .map(UriWrapper::getUri)
            .orElseThrow();
    }

    private List<URI> listFiles(ImportRequest importRequest) {

        S3Driver s3Driver = new S3Driver(s3Client, importRequest.extractBucketFromS3Location());
        List<UnixPath> filenames = s3Driver.listAllFiles(importRequest.extractPathFromS3Location());
        logger.info(attempt(() -> s3ImportsMapper.writeValueAsString(filenames)).orElseThrow());
        return filenames.stream()
            .map(filename -> createUri(importRequest.getS3Location(), filename))
            .collect(Collectors.toList());
    }

    private void logWarningForNotEmittedFilenames(List<PutEventsResult> failedRequests) {
        if (!failedRequests.isEmpty()) {
            String failedRequestsString = failedRequests
                .stream()
                .map(PutEventsResult::toString)
                .collect(Collectors.joining(LINE_SEPARATOR));
            logger.warn(NON_EMITTED_FILENAMES_WARNING_PREFIX + failedRequestsString);
        }
    }

    private List<URI> collectNotEmittedFilenames(List<PutEventsResult> failedRequests) {
        return failedRequests.stream()
            .map(PutEventsResult::getRequest)
            .map(PutEventsRequest::entries)
            .flatMap(Collection::stream)
            .map(PutEventsRequestEntry::detail)
            .map(ImportRequest::fromJson)
            .map(ImportRequest::getS3Location)
            .collect(Collectors.toList());
    }

    private List<PutEventsResult> emitEvents(Context context, List<URI> files) {

        BatchEventEmitter<ImportRequest> batchEventEmitter =
            new BatchEventEmitter<>(RUNNING_CLASS_NAME,
                                    context.getInvokedFunctionArn(),
                                    eventBridgeClient);

        List<ImportRequest> filenameEvents = files.stream()
            .map(this::newImportRequestForSingleFile)
            .collect(Collectors.toList());
        batchEventEmitter.addEvents(filenameEvents);
        return batchEventEmitter.emitEvents(NUMBER_OF_EMITTED_FILENAMES_PER_BATCH);
    }

    private ImportRequest newImportRequestForSingleFile(URI uri) {
        return new ImportRequest(FILENAME_EMISSION_EVENT_TOPIC, FILENAME_EMISSION_EVENT_SUBTOPIC, uri, timestamp);
    }

    private void validateImportRequest(ImportRequest importRequest, List<URI> files) {
        if (isNull(files) || files.isEmpty()) {
            throw new IllegalArgumentException(WRONG_OR_EMPTY_S3_LOCATION_ERROR + importRequest.getS3Location());
        }
        if (StringUtils.isNotBlank(importRequest.getSubtopic())) {
            throw new IllegalArgumentException(
                INFORM_USER_THAT_EVENT_TYPE_IS_SET_IN_ENV + importRequest.toJsonString());
        }
    }

    private ImportRequest parseInput(InputStream input) {
        String inputString = IoUtils.streamToString(input);
        return attempt(() -> ImportRequest.fromJson(inputString))
            .orElseThrow(fail -> new IllegalArgumentException(EXPECTED_BODY_MESSAGE + inputString));
    }

    private <T> void writeOutput(OutputStream output, List<T> results) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(output, StandardCharsets.UTF_8))) {
            writer.write(toJson(results));
        }
    }

    private <T> String toJson(T results) throws JsonProcessingException {
        return s3ImportsMapper.writeValueAsString(results);
    }
}
