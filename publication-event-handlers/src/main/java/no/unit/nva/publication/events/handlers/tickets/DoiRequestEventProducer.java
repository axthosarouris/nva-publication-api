package no.unit.nva.publication.events.handlers.tickets;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static nva.commons.core.attempt.Try.attempt;
import com.amazonaws.services.lambda.runtime.Context;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import no.unit.nva.events.handlers.DestinationsEventBridgeEventHandler;
import no.unit.nva.events.models.AwsEventBridgeDetail;
import no.unit.nva.events.models.AwsEventBridgeEvent;
import no.unit.nva.events.models.EventReference;
import no.unit.nva.model.Publication;
import no.unit.nva.publication.doi.update.dto.DoiRegistrarEntryFields;
import no.unit.nva.publication.events.bodies.DataEntryUpdateEvent;
import no.unit.nva.publication.events.bodies.DoiMetadataUpdateEvent;
import no.unit.nva.publication.exception.InvalidInputException;
import no.unit.nva.publication.model.business.DoiRequest;
import no.unit.nva.publication.model.business.Entity;
import no.unit.nva.publication.model.business.Resource;
import no.unit.nva.publication.model.business.TicketStatus;
import no.unit.nva.publication.service.impl.ResourceService;
import no.unit.nva.s3.S3Driver;
import nva.commons.core.JacocoGenerated;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * Sends messages to DoiRegistrar service for creating and updating DOIs.
 */
public class DoiRequestEventProducer
    extends DestinationsEventBridgeEventHandler<EventReference, DoiMetadataUpdateEvent> {
    
    public static final Duration MIN_INTERVAL_FOR_REREQUESTING_A_DOI = Duration.ofSeconds(10);
    public static final String DOI_REQUEST_HAS_NO_IDENTIFIER = "DoiRequest has no identifier";
    public static final String HEAD = "HEAD";
    public static final DoiMetadataUpdateEvent EMPTY_EVENT = DoiMetadataUpdateEvent.empty();
    protected static final Integer HTTP_FOUND = 302;
    private static final String HANDLER_DOES_NOT_DEAL_WITH_DELETIONS = "Handler does not deal with deletions";
    private final ResourceService resourceService;
    private final HttpClient httpClient;
    private final S3Client s3Client;
    
    @JacocoGenerated
    public DoiRequestEventProducer() {
        this(ResourceService.defaultService(), HttpClient.newHttpClient(), S3Driver.defaultS3Client().build());
    }
    
    public DoiRequestEventProducer(ResourceService resourceService, HttpClient httpClient, S3Client s3Client) {
        super(EventReference.class);
        this.resourceService = resourceService;
        this.httpClient = httpClient;
        this.s3Client = s3Client;
    }
    
    @Override
    protected DoiMetadataUpdateEvent processInputPayload(
        EventReference inputEvent,
        AwsEventBridgeEvent<AwsEventBridgeDetail<EventReference>> event,
        Context context) {
        var s3Driver = new S3Driver(s3Client, inputEvent.getUri().getHost());
        var eventString = s3Driver.readEvent(inputEvent.getUri());
        var eventBody = DataEntryUpdateEvent.fromJson(eventString);
        validate(eventBody);
        
        return isEffectiveChange(eventBody)
                   ? propagateEvent(eventBody)
                   : EMPTY_EVENT;
    }
    
    private DoiMetadataUpdateEvent propagateEvent(DataEntryUpdateEvent input) {
        var newEntry = input.getNewData();
        
        if (newEntry instanceof Resource) {
            return createDoiMetadataUpdateEvent((Resource) newEntry);
        }
        if (newEntry instanceof DoiRequest) {
            return createDoiMetadataUpdateEvent((DoiRequest) input.getOldData(), (DoiRequest) newEntry);
        }
        return EMPTY_EVENT;
    }
    
    private DoiMetadataUpdateEvent createDoiMetadataUpdateEvent(DoiRequest oldEntry, DoiRequest newEntry) {
        if (isFirstDoiRequest(oldEntry, newEntry)) {
            return DoiMetadataUpdateEvent.createNewDoiEvent(newEntry, resourceService);
        } else if (isDoiRequestApproval(oldEntry, newEntry)) {
            return createEventForMakingDoiFindable(newEntry);
        }
        return EMPTY_EVENT;
    }
    
    private DoiMetadataUpdateEvent createDoiMetadataUpdateEvent(Resource newEntry) {
        if (resourceWithFindableDoiHasBeenUpdated(newEntry)) {
            var publication = newEntry.toPublication();
            return DoiMetadataUpdateEvent.createUpdateDoiEvent(publication);
        }
        return EMPTY_EVENT;
    }
    
    private DoiMetadataUpdateEvent createEventForMakingDoiFindable(DoiRequest newEntry) {
        return DoiMetadataUpdateEvent.createUpdateDoiEvent(newEntry.toPublication(resourceService));
    }
    
    private boolean isDoiRequestApproval(DoiRequest oldEntry, DoiRequest newEntry) {
        var oldEntryIsNotApproved = matchStatus(oldEntry, TicketStatus.PENDING);
        var newEntryIsApproved = matchStatus(newEntry, TicketStatus.COMPLETED);
        return oldEntryIsNotApproved && newEntryIsApproved;
    }
    
    private Boolean matchStatus(DoiRequest oldEntry, TicketStatus approved) {
        return Optional.of(oldEntry)
                   .map(DoiRequest::getStatus)
                   .map(approved::equals)
                   .orElse(false);
    }
    
    private boolean resourceWithFindableDoiHasBeenUpdated(Resource newEntry) {
        return hasDoi(newEntry) && doiIsFindable(newEntry.getDoi());
    }
    
    private boolean doiIsFindable(URI doi) {
        var request = HttpRequest.newBuilder(doi).method(HEAD, BodyPublishers.noBody()).build();
        return attempt(() -> httpClient.send(request, BodyHandlers.ofString()))
                   .map(HttpResponse::statusCode)
                   .map(HTTP_FOUND::equals)
                   .toOptional()
                   .orElse(false);
    }
    
    private boolean hasDoi(Resource newEntry) {
        return nonNull(newEntry.getDoi());
    }
    
    private void validate(DataEntryUpdateEvent event) {
        validateEvent(event);
        validateDoiRequest(event);
    }
    
    private void validateEvent(DataEntryUpdateEvent event) {
        if (eventIsADeletion(event)) {
            throw new InvalidInputException(HANDLER_DOES_NOT_DEAL_WITH_DELETIONS);
        }
    }
    
    private void validateDoiRequest(DataEntryUpdateEvent event) {
        if (event.getNewData() instanceof DoiRequest) {
            var doiRequest = (DoiRequest) event.getNewData();
            if (eventCannotBeReferenced(doiRequest)) {
                throw new IllegalStateException(DOI_REQUEST_HAS_NO_IDENTIFIER);
            }
        }
    }
    
    private boolean eventIsADeletion(DataEntryUpdateEvent event) {
        return isNull(event.getNewData());
    }
    
    private boolean eventCannotBeReferenced(DoiRequest doiRequest) {
        return isNull(doiRequest.getIdentifier());
    }
    
    private Publication toPublication(Entity dataEntry) {
        return nonNull(dataEntry)
                   ? dataEntry.toPublication(resourceService)
                   : null;
    }
    
    private boolean isFirstDoiRequest(DoiRequest oldEntry, DoiRequest newEntry) {
        return thereHasBeenNoRecentDoiRequest(oldEntry, newEntry) && publicationDoesNotHaveDoiFromBefore(newEntry);
    }
    
    private boolean thereHasBeenNoRecentDoiRequest(DoiRequest oldEntry, DoiRequest newEntry) {
        return isNull(oldEntry) || isOldDoiRequest(oldEntry, newEntry);
    }
    
    private boolean isOldDoiRequest(DoiRequest oldEntry, DoiRequest newEntry) {
        var latestDateForRerequestingDoi = oldEntry.getModifiedDate().plus(MIN_INTERVAL_FOR_REREQUESTING_A_DOI);
        return newEntry.getModifiedDate().isAfter(latestDateForRerequestingDoi);
    }
    
    private boolean publicationDoesNotHaveDoiFromBefore(DoiRequest doiRequest) {
        return attempt(() -> resourceService.getPublicationByIdentifier(doiRequest.extractPublicationIdentifier()))
                   .map(Publication::getDoi)
                   .map(Objects::isNull)
                   .orElseThrow();
    }
    
    private boolean isEffectiveChange(DataEntryUpdateEvent updateEvent) {
        return isDoiRequestUpdate(updateEvent) || doiRelatedDataDifferFromOld(updateEvent);
    }
    
    private boolean doiRelatedDataDifferFromOld(DataEntryUpdateEvent updateEvent) {
        var newPublication = toPublication(updateEvent.getNewData());
        var oldPublication = toPublication(updateEvent.getOldData());
        if (nonNull(newPublication)) {
            return newDoiRelatedMetadataDifferFromOld(newPublication, oldPublication);
        }
        return false;
    }
    
    private boolean isDoiRequestUpdate(DataEntryUpdateEvent updateEvent) {
        return updateEvent.getNewData() instanceof DoiRequest;
    }
    
    private boolean newDoiRelatedMetadataDifferFromOld(Publication newPublication, Publication oldPublication) {
        DoiRegistrarEntryFields doiInfoNew = DoiRegistrarEntryFields.fromPublication(newPublication);
        DoiRegistrarEntryFields doiInfoOld = extractInfoFromOldInstance(oldPublication);
        return !doiInfoNew.equals(doiInfoOld);
    }
    
    private DoiRegistrarEntryFields extractInfoFromOldInstance(Publication oldPublication) {
        return nonNull(oldPublication) ? DoiRegistrarEntryFields.fromPublication(oldPublication) : null;
    }
}
