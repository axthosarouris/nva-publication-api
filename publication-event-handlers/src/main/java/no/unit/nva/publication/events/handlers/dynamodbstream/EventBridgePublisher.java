package no.unit.nva.publication.events.handlers.dynamodbstream;

import com.amazonaws.services.lambda.runtime.events.DynamodbEvent;
import com.amazonaws.services.lambda.runtime.events.DynamodbEvent.DynamodbStreamRecord;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import nva.commons.core.JacocoGenerated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequest;
import software.amazon.awssdk.services.eventbridge.model.PutEventsRequestEntry;

public class EventBridgePublisher implements EventPublisher {
    
    public static final String EVENT_SOURCE = "aws-dynamodb-stream-eventbridge-fanout";
    public static final int ENTRIES_PER_REQUEST = 7;
    private static final ObjectMapper objectMapper = new ObjectMapper()
                                                         .setSerializationInclusion(JsonInclude.Include.NON_NULL);
    private static final Logger logger = LoggerFactory.getLogger(EventBridgePublisher.class);
    private final EventBridgeRetryClient eventBridge;
    private final EventPublisher failedEventPublisher;
    private final String eventBusName;
    private final Clock clock;
    private final String eventDetailType;
    
    @JacocoGenerated
    public EventBridgePublisher(EventBridgeRetryClient eventBridge,
                                EventPublisher failedEventPublisher,
                                String eventBusName,
                                String eventDetailType) {
        this(eventBridge, failedEventPublisher, eventBusName, eventDetailType, Clock.systemUTC());
    }
    
    public EventBridgePublisher(EventBridgeRetryClient eventBridge,
                                EventPublisher failedEventPublisher,
                                String eventBusName,
                                String eventDetailType,
                                Clock clock) {
        this.eventBridge = eventBridge;
        this.failedEventPublisher = failedEventPublisher;
        this.eventBusName = eventBusName;
        this.clock = clock;
        this.eventDetailType = eventDetailType;
    }
    
    @Override
    public void publish(final DynamodbEvent event) {
        List<PutEventsRequestEntry> requestEntries = createPutEventsRequestEntries(event);
        List<PutEventsRequestEntry> failedEntries = putEventsToEventBus(
            requestEntries);
        publishFailedEventsToDlq(failedEntries);
    }
    
    private void publishFailedEventsToDlq(List<PutEventsRequestEntry> failedEntries) {
        if (!failedEntries.isEmpty()) {
            logger.debug("Sending failed events {} to failed event publisher", failedEntries);
            failedEntries.forEach(this::publishFailedEvent);
        }
    }
    
    private List<PutEventsRequestEntry> putEventsToEventBus(List<PutEventsRequestEntry> requestEntries) {
        List<List<PutEventsRequestEntry>> groupedRequestEntries = Lists.partition(requestEntries, ENTRIES_PER_REQUEST);
        return groupedRequestEntries.stream()
                   .map(this::createPutEventsRequest)
                   .map(eventBridge::putEvents)
                   .flatMap(Collection::stream)
                   .collect(Collectors.toList());
    }
    
    private PutEventsRequest createPutEventsRequest(List<PutEventsRequestEntry> requestEntries) {
        return PutEventsRequest.builder()
                   .entries(requestEntries)
                   .build();
    }
    
    private List<PutEventsRequestEntry> createPutEventsRequestEntries(DynamodbEvent event) {
        return event.getRecords()
                   .stream()
                   .map(this::createPutEventRequestEntry)
                   .collect(Collectors.toList());
    }
    
    private PutEventsRequestEntry createPutEventRequestEntry(DynamodbStreamRecord record) {
        Instant time = Instant.now(clock);
        return PutEventsRequestEntry.builder()
                   .eventBusName(eventBusName)
                   .time(time)
                   .source(EVENT_SOURCE)
                   .detailType(eventDetailType)
                   .detail(toString(record))
                   .resources(record.getEventSourceARN())
                   .build();
    }
    
    private void publishFailedEvent(PutEventsRequestEntry entry) {
        DynamodbEvent.DynamodbStreamRecord record = parseDynamodbStreamRecord(entry);
        DynamodbEvent failedEvent = new DynamodbEvent();
        failedEvent.setRecords(Collections.singletonList(record));
        failedEventPublisher.publish(failedEvent);
    }
    
    @JacocoGenerated
    private String toString(DynamodbEvent.DynamodbStreamRecord record) {
        try {
            return objectMapper.writeValueAsString(record);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }
    
    @JacocoGenerated
    private DynamodbStreamRecord parseDynamodbStreamRecord(PutEventsRequestEntry entry) {
        try {
            return objectMapper.readValue(entry.detail(), DynamodbStreamRecord.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }
}