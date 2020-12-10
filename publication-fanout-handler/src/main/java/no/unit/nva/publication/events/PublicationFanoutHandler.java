package no.unit.nva.publication.events;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.DynamodbEvent;
import com.amazonaws.services.lambda.runtime.events.models.dynamodb.AttributeValue;
import no.unit.nva.events.handlers.EventHandler;
import no.unit.nva.events.models.AwsEventBridgeEvent;
import no.unit.nva.model.Publication;
import nva.commons.utils.JacocoGenerated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Optional;

import static no.unit.nva.publication.events.DynamodbStreamRecordPublicationMapper.toPublication;

public class PublicationFanoutHandler
        extends EventHandler<DynamodbEvent.DynamodbStreamRecord, PublicationUpdateEvent> {

    private static final Logger logger = LoggerFactory.getLogger(PublicationFanoutHandler.class);
    public static final String MAPPING_ERROR = "Error mapping Dynamodb Image to Publication";
    public static final Publication NO_VALUE = null;

    @JacocoGenerated
    public PublicationFanoutHandler() {
        super(DynamodbEvent.DynamodbStreamRecord.class);
    }

    @Override
    protected PublicationUpdateEvent processInput(
            DynamodbEvent.DynamodbStreamRecord input,
            AwsEventBridgeEvent<DynamodbEvent.DynamodbStreamRecord> event,
            Context context) {
        Optional<Publication> oldPublication = getPublication(input.getDynamodb().getOldImage());
        Optional<Publication> newPublication = getPublication(input.getDynamodb().getNewImage());
        String updateType = input.getEventName();

        return new PublicationUpdateEvent(
                updateType,
                oldPublication.orElse(NO_VALUE),
                newPublication.orElse(NO_VALUE)
        );
    }

    private Optional<Publication> getPublication(Map<String, AttributeValue> image) {
        if (image == null) {
            return Optional.empty();
        }
        try {
            var publication = toPublication(image);
            return Optional.of(publication);
        } catch (Exception e) {
            logger.error(MAPPING_ERROR, e);
            throw new RuntimeException(MAPPING_ERROR, e);
        }
    }
}
