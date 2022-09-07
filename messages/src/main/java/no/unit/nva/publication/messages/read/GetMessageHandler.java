package no.unit.nva.publication.messages.read;

import static java.net.HttpURLConnection.HTTP_SEE_OTHER;
import static no.unit.nva.publication.PublicationServiceConfig.API_HOST;
import static no.unit.nva.publication.PublicationServiceConfig.PUBLICATION_IDENTIFIER_PATH_PARAMETER;
import static no.unit.nva.publication.PublicationServiceConfig.PUBLICATION_PATH;
import static no.unit.nva.publication.messages.MessageApiConfig.LOCATION_HEADER;
import static no.unit.nva.publication.messages.MessageApiConfig.TICKET_IDENTIFIER_PATH_PARAMETER;
import static no.unit.nva.publication.messages.MessageApiConfig.TICKET_PATH;
import static nva.commons.core.attempt.Try.attempt;
import com.amazonaws.services.lambda.runtime.Context;
import java.util.Map;
import no.unit.nva.identifiers.SortableIdentifier;
import nva.commons.apigateway.ApiGatewayHandler;
import nva.commons.apigateway.RequestInfo;
import nva.commons.core.JacocoGenerated;
import nva.commons.core.paths.UriWrapper;

public class GetMessageHandler extends ApiGatewayHandler<Void, Void> {
    
    @JacocoGenerated
    public GetMessageHandler() {
        super(Void.class);
    }
    
    @Override
    protected Void processInput(Void input, RequestInfo requestInfo, Context context) {
        var resourceIdentifier = extractIdentifier(requestInfo, PUBLICATION_IDENTIFIER_PATH_PARAMETER);
        var ticketIdentifier = extractIdentifier(requestInfo, TICKET_IDENTIFIER_PATH_PARAMETER);
        var redirectUri = UriWrapper.fromHost(API_HOST)
                              .addChild(PUBLICATION_PATH)
                              .addChild(resourceIdentifier.toString())
                              .addChild(TICKET_PATH)
                              .addChild(ticketIdentifier.toString())
                              .getUri();
        addAdditionalHeaders(() -> Map.of(LOCATION_HEADER, redirectUri.toString()));
        return null;
    }
    
    @Override
    protected Integer getSuccessStatusCode(Void input, Void output) {
        return HTTP_SEE_OTHER;
    }
    
    private SortableIdentifier extractIdentifier(RequestInfo requestInfo, String pathParameter) {
        return attempt(() -> new SortableIdentifier(requestInfo.getPathParameter(pathParameter)))
                   .orElseThrow();
    }
}
