package no.unit.nva.doi.handler;

import static nva.commons.core.JsonUtils.objectMapper;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.lambda.runtime.Context;
import no.unit.nva.doi.UpdateDoiStatusProcess;
import no.unit.nva.events.handlers.DestinationsEventBridgeEventHandler;
import no.unit.nva.events.models.AwsEventBridgeDetail;
import no.unit.nva.events.models.AwsEventBridgeEvent;
import no.unit.nva.publication.doi.update.dto.DoiUpdateHolder;
import no.unit.nva.publication.service.PublicationService;
import no.unit.nva.publication.service.impl.DynamoDBPublicationService;
import nva.commons.core.Environment;
import nva.commons.core.JacocoGenerated;

public class UpdateDoiStatusHandler extends DestinationsEventBridgeEventHandler<DoiUpdateHolder, Void> {

    public static final Void SUCCESSESFULLY_HANDLED_EVENT = null;
    private final PublicationService publicationService;

    /**
     * Default constructor for MainHandler.
     */
    @JacocoGenerated
    public UpdateDoiStatusHandler() {
        this(defaultDynamoDBPublicationService());
    }

    /**
     * Constructor for MainHandler.
     *
     * @param publicationService publicationService
     */
    public UpdateDoiStatusHandler(PublicationService publicationService) {
        super(DoiUpdateHolder.class);
        this.publicationService = publicationService;
    }

    @Override
    protected Void processInputPayload(DoiUpdateHolder input,
                                       AwsEventBridgeEvent<AwsEventBridgeDetail<DoiUpdateHolder>> event,
                                       Context context) {
        new UpdateDoiStatusProcess(publicationService, input).updateDoiStatus();
        return SUCCESSESFULLY_HANDLED_EVENT;
    }

    @JacocoGenerated
    private static DynamoDBPublicationService defaultDynamoDBPublicationService() {
        return new DynamoDBPublicationService(
            AmazonDynamoDBClientBuilder.defaultClient(),
            objectMapper,
            new Environment());
    }
}
