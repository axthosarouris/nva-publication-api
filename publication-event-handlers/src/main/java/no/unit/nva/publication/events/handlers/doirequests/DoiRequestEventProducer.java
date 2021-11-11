package no.unit.nva.publication.events.handlers.doirequests;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import com.amazonaws.services.lambda.runtime.Context;
import java.util.Optional;
import no.unit.nva.events.handlers.DestinationsEventBridgeEventHandler;
import no.unit.nva.events.models.AwsEventBridgeDetail;
import no.unit.nva.events.models.AwsEventBridgeEvent;
import no.unit.nva.model.DoiRequestStatus;
import no.unit.nva.model.Publication;
import no.unit.nva.publication.doi.update.dto.DoiRegistrarEntryFields;
import no.unit.nva.publication.doi.update.dto.PublicationHolder;
import no.unit.nva.publication.events.bodies.DynamoEntryUpdateEvent;
import no.unit.nva.publication.storage.model.DoiRequest;
import no.unit.nva.publication.storage.model.ResourceUpdate;
import nva.commons.core.JacocoGenerated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Consumes DynamodbEvent's that's been published on EventBridge, and produces new PublicationCollection DTO with type
 * `publication.doirequest`.
 */
public class DoiRequestEventProducer
    extends DestinationsEventBridgeEventHandler<DynamoEntryUpdateEvent, PublicationHolder> {

    public static final String TYPE_REQUEST_FOR_NEW_DRAFT_DOI = "publication.doiupdate.newdraftdoirequest";
    public static final String TYPE_UPDATE_EXISTING_DOI = "publication.doiupdate.updateexistingdoi";
    public static final String NO_RESOURCE_IDENTIFIER_ERROR = "Resource has no identifier:";

    protected static final String EMPTY_EVENT_TYPE = "empty";
    public static final PublicationHolder EMPTY_EVENT = emptyEvent();
    private static final Logger logger = LoggerFactory.getLogger(DoiRequestEventProducer.class);

    @JacocoGenerated
    public DoiRequestEventProducer() {
        super(DynamoEntryUpdateEvent.class);
    }

    @Override
    protected PublicationHolder processInputPayload(
        DynamoEntryUpdateEvent input,
        AwsEventBridgeEvent<AwsEventBridgeDetail<DynamoEntryUpdateEvent>> event,
        Context context) {

        logger.info(event.toJsonString());
        PublicationHolder updatedDoiInformationEvent = fromDynamoEntryUpdate(input);
        validate(updatedDoiInformationEvent);
        return updatedDoiInformationEvent;
    }

    private static PublicationHolder emptyEvent() {
        return toPublicationHolder(null, EMPTY_EVENT_TYPE);
    }

    private static PublicationHolder toPublicationHolder(DoiRequest doiRequest, String eventType) {
        Publication publication = nonNull(doiRequest) ? doiRequest.toPublication() : null;
        return new PublicationHolder(eventType, publication);
    }

    private void validate(PublicationHolder updatedDoiInformationEvent) {
        if (isInvalid(updatedDoiInformationEvent)) {
            throw new IllegalStateException(NO_RESOURCE_IDENTIFIER_ERROR);
        }
    }

    private boolean isInvalid(PublicationHolder updatedDoiInformationEvent) {
        return nonNull(updatedDoiInformationEvent.getItem())
               && isNull(updatedDoiInformationEvent.getItem().getIdentifier());
    }

    private PublicationHolder fromDynamoEntryUpdate(DynamoEntryUpdateEvent updateEvent) {
        return Optional.of(updateEvent)
            .filter(this::shouldPropagateEvent)
            .map(DynamoEntryUpdateEvent::getNewData)
            .filter(data -> data instanceof DoiRequest)
            .map(data -> (DoiRequest) data)
            .map(pub -> toPublicationHolder(pub, calculateEventType(updateEvent)))
            .orElse(EMPTY_EVENT);
    }

    private String calculateEventType(DynamoEntryUpdateEvent updateEvent) {
        if (isFirstDoiRequest(updateEvent)) {
            return TYPE_REQUEST_FOR_NEW_DRAFT_DOI;
        } else {
            return TYPE_UPDATE_EXISTING_DOI;
        }
    }

    private Publication toPublication(ResourceUpdate resourceUpdate) {
        return resourceUpdate != null ? resourceUpdate.toPublication() : null;
    }

    private boolean isFirstDoiRequest(DynamoEntryUpdateEvent updateEvent) {
        return isNull(toPublication(updateEvent.getOldData()))
               && updateHasDoiRequest(updateEvent)
               && isNull(toPublication(updateEvent.getNewData()).getDoi());
    }

    private boolean updateHasDoiRequest(DynamoEntryUpdateEvent updateEvent) {
        return nonNull(toPublication(updateEvent.getNewData()))
               && nonNull(toPublication(updateEvent.getNewData()).getDoiRequest());
    }

    private boolean publicationHasDoiRequest(DynamoEntryUpdateEvent updateEvent) {
        return Optional.of(updateEvent)
            .map(DynamoEntryUpdateEvent::getNewData)
            .filter(data -> data instanceof DoiRequest)
            .map(data -> (DoiRequest) data)
            .isPresent();
    }

    private boolean shouldPropagateEvent(DynamoEntryUpdateEvent updateEvent) {
        boolean publicationHasDoiRequest = publicationHasDoiRequest(updateEvent);
        boolean isChange = isEffectiveChange(updateEvent);

        return isChange && publicationHasDoiRequest;
    }

    private boolean isEffectiveChange(DynamoEntryUpdateEvent updateEvent) {
        var newPublication = toPublication(updateEvent.getNewData());
        var oldPublication = toPublication(updateEvent.getOldData());
        if (nonNull(newPublication)) {
            return
                doiRequestGotApproved(updateEvent)
                || newDoiRelatedMetadataDifferFromOld(newPublication, oldPublication);
        }
        return false;
    }

    private boolean doiRequestGotApproved(DynamoEntryUpdateEvent updateEvent) {
        DoiRequestStatus oldStatus = extractOldPublicationStatus(updateEvent);
        DoiRequestStatus newStatus = extractNewPublicationStatus(updateEvent);
        return DoiRequestStatus.REQUESTED.equals(oldStatus) && DoiRequestStatus.APPROVED.equals(newStatus);
    }

    private DoiRequestStatus extractNewPublicationStatus(DynamoEntryUpdateEvent updateEvent) {
        return Optional.of(updateEvent)
            .map(DynamoEntryUpdateEvent::getNewData)
            .filter(data -> data instanceof DoiRequest)
            .map(data -> (DoiRequest) data)
            .map(DoiRequest::getStatus)
            .orElse(null);
    }

    private DoiRequestStatus extractOldPublicationStatus(DynamoEntryUpdateEvent updateEvent) {
        return Optional.of(updateEvent)
            .map(DynamoEntryUpdateEvent::getOldData)
            .filter(data -> data instanceof DoiRequest)
            .map(data -> (DoiRequest) data)
            .map(DoiRequest::getStatus)
            .orElse(DoiRequestStatus.REQUESTED);
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
