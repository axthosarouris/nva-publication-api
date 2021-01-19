package no.unit.nva.publication.fetch;

import static nva.commons.core.JsonUtils.objectMapper;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.lambda.runtime.Context;
import com.fasterxml.jackson.databind.JsonNode;
import no.unit.nva.model.Publication;
import no.unit.nva.model.util.ContextUtil;
import no.unit.nva.publication.JsonLdContextUtil;
import no.unit.nva.publication.RequestUtil;
import no.unit.nva.publication.service.PublicationService;
import no.unit.nva.publication.service.impl.DynamoDBPublicationService;
import nva.commons.apigateway.ApiGatewayHandler;
import nva.commons.apigateway.RequestInfo;
import nva.commons.apigateway.exceptions.ApiGatewayException;
import nva.commons.core.Environment;
import nva.commons.core.JacocoGenerated;
import org.apache.http.HttpStatus;
import org.slf4j.LoggerFactory;

public class FetchPublicationHandler extends ApiGatewayHandler<Void, JsonNode> {

    public static final String PUBLICATION_CONTEXT_JSON = "publicationContext.json";

    private final PublicationService publicationService;

    /**
     * Default constructor for MainHandler.
     */
    @JacocoGenerated
    public FetchPublicationHandler() {
        this(new DynamoDBPublicationService(
                AmazonDynamoDBClientBuilder.defaultClient(),
                objectMapper,
                new Environment()),
            new Environment());
    }

    /**
     * Constructor for MainHandler.
     *
     * @param publicationService publicationService
     * @param environment        environment
     */
    public FetchPublicationHandler(PublicationService publicationService,
                                   Environment environment) {
        super(Void.class, environment, LoggerFactory.getLogger(FetchPublicationHandler.class));
        this.publicationService = publicationService;
    }

    @Override
    protected JsonNode processInput(Void input, RequestInfo requestInfo, Context context) throws ApiGatewayException {
        Publication publication = publicationService.getPublication(
            RequestUtil.getIdentifier(requestInfo));
        return toJsonNodeWithContext(publication);
    }

    private JsonNode toJsonNodeWithContext(Publication publication) {
        JsonNode publicationJson = objectMapper.valueToTree(publication);
        new JsonLdContextUtil(objectMapper)
            .getPublicationContext(PUBLICATION_CONTEXT_JSON)
            .ifPresent(publicationContext -> ContextUtil.injectContext(publicationJson, publicationContext));
        return publicationJson;
    }

    @Override
    protected Integer getSuccessStatusCode(Void input, JsonNode output) {
        return HttpStatus.SC_OK;
    }
}
