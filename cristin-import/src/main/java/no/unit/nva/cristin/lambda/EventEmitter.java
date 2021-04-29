package no.unit.nva.cristin.lambda;

import static no.unit.nva.cristin.lambda.ApplicationConstants.EVENT_BUS_NAME;
import static nva.commons.core.attempt.Try.attempt;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import nva.commons.core.JsonSerializable;
import nva.commons.core.JsonUtils;
import org.apache.commons.collections4.ListUtils;
import software.amazon.awssdk.services.eventbridge.EventBridgeClient;
import software.amazon.awssdk.services.eventbridge.model.EventBus;
import software.amazon.awssdk.services.eventbridge.model.ListEventBusesRequest;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequest;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequestEntry;
import software.amazon.awssdk.services.eventbridge.model.PutEventsResponse;

/**
 * This class accepts a set of colletion of {@link JsonSerializable} objects and emits one event per object in
 * EventBridge. The events have the following format:
 *
 * <p><pre>{@code
 * {
 *   "version": "0", // set by AWS automatically
 *   "id": "6a7e8feb-b491-4cf7-a9f1-bf3703467718", // set by AWS automatically
 *   "detail-type": "<supplied by user>", //all events have the same detail-type
 *   "source": "<supplied_invoked_function_arn>",
 *   "account": "111122223333", //set by AWS.
 *   "time": "2017-12-22T18:43:48Z", // Instant.now()
 *   "region": "us-west-1",//set by AWS.
 *   "resources": [
 *     <supplied_invoked_function_arn>
 *   ],
 *   "detail": {<supplied_body>}
 * }
 * }</pre>
 *
 * @param <T> the type of the event detail.
 */
public class EventEmitter<T> {

    public static final String BUS_NOT_FOUND_ERROR = "EventBridge bus not found: ";
    private static final int BATCH_SIZE = 10;
    private static final int MAX_ATTEMPTS = 10;
    private final String detailType;
    private final String invokingFunctionArn;
    private final EventBridgeClient client;
    private List<PutEventsRequest> putEventsRequests;
    private final String eventSource;

    public EventEmitter(String detailType,
                        String eventSource,
                        String invokingFunctionArn,
                        EventBridgeClient eventBridgeClient) {
        this.detailType = detailType;
        this.invokingFunctionArn = invokingFunctionArn;
        this.client = eventBridgeClient;
        this.eventSource = eventSource;
    }

    /**
     * Create one event for every element in the "eventDetails" list.
     *
     * @param eventDetails A collection of details for the respective events.
     */
    public void addEvents(Collection<T> eventDetails) {
        List<PutEventsRequestEntry> eventRequestEntries =
            eventDetails.stream().map(this::createPutEventRequestEntry).collect(Collectors.toList());
        putEventsRequests = createBatchesOfPutEventsRequests(eventRequestEntries);
    }

    /**
     * This method emits the events in AWS EventBridge.
     *
     * @return The failed requests and the respective responses from EventBridge.
     */
    public List<PutEventsResult> emitEvents() {
        checkBus();
        List<PutEventsResult> failedEvents = tryManyTimesToEmitTheEvents();
        if (failedEvents != null) {
            return failedEvents;
        }
        return Collections.emptyList();
    }

    private List<PutEventsResult> tryManyTimesToEmitTheEvents() {
        List<PutEventsResult> failedEvents = emitEventsAndCollectFailures(putEventsRequests);
        int attempts = 0;
        while (!failedEvents.isEmpty() && attempts < MAX_ATTEMPTS) {
            List<PutEventsRequest> requestsToResend = collectRequestsForResending(failedEvents);
            failedEvents = emitEventsAndCollectFailures(requestsToResend);
            attempts++;
        }
        if (!failedEvents.isEmpty()) {
            return failedEvents;
        }
        return null;
    }

    private void checkBus() {
        List<String> busNames =
            client.listEventBuses(ListEventBusesRequest.builder().namePrefix(EVENT_BUS_NAME).build())
                .eventBuses()
                .stream()
                .map(EventBus::name)
                .collect(Collectors.toList());
        if (!busNames.contains(EVENT_BUS_NAME)) {
            throw new IllegalStateException(BUS_NOT_FOUND_ERROR + EVENT_BUS_NAME);
        }
    }

    private PutEventsRequestEntry createPutEventRequestEntry(T eventDetail) {

        return PutEventsRequestEntry.builder()
                   .eventBusName(EVENT_BUS_NAME)
                   .resources(invokingFunctionArn)
                   .detailType(detailType)
                   .time(Instant.now())
                   .detail(toJson(eventDetail))
                   .source(eventSource)
                   .build();
    }

    private String toJson(T eventDetail) {
        return attempt(() -> JsonUtils.objectMapperNoEmpty.writeValueAsString(eventDetail)).orElseThrow();
    }

    private List<PutEventsRequest> createBatchesOfPutEventsRequests(List<PutEventsRequestEntry> entries) {
        return ListUtils.partition(entries, BATCH_SIZE)
                   .stream()
                   .map(batch -> PutEventsRequest.builder().entries(batch).build())
                   .collect(Collectors.toList());
    }

    private List<PutEventsRequest> collectRequestsForResending(List<PutEventsResult> failedEvents) {
        return failedEvents.stream().map(PutEventsResult::getRequest).collect(Collectors.toList());
    }

    private List<PutEventsResult> emitEventsAndCollectFailures(
        List<PutEventsRequest> eventRequests) {
        return eventRequests.stream()
                   .map(this::emitEvent)
                   .filter(PutEventsResult::hasFailures)
                   .collect(Collectors.toList());
    }

    private PutEventsResult emitEvent(PutEventsRequest request) {
        PutEventsResponse result = client.putEvents(request);
        return new PutEventsResult(request, result);
    }
}
