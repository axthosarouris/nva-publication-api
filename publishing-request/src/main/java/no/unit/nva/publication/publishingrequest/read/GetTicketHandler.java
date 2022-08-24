package no.unit.nva.publication.publishingrequest.read;

import static java.net.HttpURLConnection.HTTP_OK;
import static no.unit.nva.publication.PublicationServiceConfig.PUBLICATION_IDENTIFIER_PATH_PARAMETER;
import com.amazonaws.services.lambda.runtime.Context;
import no.unit.nva.identifiers.SortableIdentifier;
import no.unit.nva.publication.model.business.TicketEntry;
import no.unit.nva.publication.model.business.UserInstance;
import no.unit.nva.publication.publishingrequest.TicketDto;
import no.unit.nva.publication.publishingrequest.TicketUtils;
import no.unit.nva.publication.service.impl.TicketService;
import nva.commons.apigateway.AccessRight;
import nva.commons.apigateway.ApiGatewayHandler;
import nva.commons.apigateway.RequestInfo;
import nva.commons.apigateway.exceptions.ApiGatewayException;
import nva.commons.apigateway.exceptions.NotFoundException;
import nva.commons.apigateway.exceptions.UnauthorizedException;

public class GetTicketHandler extends ApiGatewayHandler<Void, TicketDto> {
    
    public static final String TICKET_NOT_FOUND = "Ticket not found";
    private final TicketService ticketService;
    
    public GetTicketHandler(TicketService ticketService) {
        super(Void.class);
        this.ticketService = ticketService;
    }
    
    @Override
    protected TicketDto processInput(Void input, RequestInfo requestInfo, Context context) throws ApiGatewayException {
        var ticketIdentifier = extractTicketIdentifierFromPath(requestInfo);
        var publicationIdentifier = extractPublicationIdentifierFromPath(requestInfo);
        var ticket = fetchTicket(ticketIdentifier, requestInfo);
        validatePathParameters(publicationIdentifier, ticket);
        return TicketDto.fromTicket(ticket);
    }
    
    private TicketEntry fetchTicket(SortableIdentifier ticketIdentifier, RequestInfo requestInfo)
        throws NotFoundException, UnauthorizedException {
        return isElevatedUser(requestInfo)
                   ? fetchForElevatedUser(ticketIdentifier, requestInfo)
                   : fetchForPublicationOwner(ticketIdentifier, requestInfo);
    }
    
    private static boolean isElevatedUser(RequestInfo requestInfo) {
        return requestInfo.userIsAuthorized(AccessRight.APPROVE_DOI_REQUEST.toString());
    }
    
    private TicketEntry fetchForPublicationOwner(SortableIdentifier ticketIdentifier, RequestInfo requestInfo)
        throws UnauthorizedException, NotFoundException {
        var userInstance = UserInstance.fromRequestInfo(requestInfo);
        return ticketService.fetchTicket(userInstance, ticketIdentifier);
    }
    
    private TicketEntry fetchForElevatedUser(SortableIdentifier ticketIdentifier, RequestInfo requestInfo)
        throws NotFoundException, UnauthorizedException {
        var ticket = ticketService.fetchTicketByIdentifier(ticketIdentifier);
        validateThatUserWorksForInstitution(requestInfo, ticket);
        return ticket;
    }
    
    private static void validateThatUserWorksForInstitution(RequestInfo requestInfo, TicketEntry ticket)
        throws NotFoundException, UnauthorizedException {
        if (!ticket.getCustomerId().equals(requestInfo.getCurrentCustomer())) {
            throw new NotFoundException(TICKET_NOT_FOUND);
        }
    }
    
    private static void validatePathParameters(SortableIdentifier publicationIdentifier, TicketEntry ticket)
        throws NotFoundException {
        if (!ticket.getResourceIdentifier().equals(publicationIdentifier)) {
            throw new NotFoundException(TICKET_NOT_FOUND);
        }
    }
    
    private SortableIdentifier extractPublicationIdentifierFromPath(RequestInfo requestInfo) {
        var identifierString = requestInfo.getPathParameter(PUBLICATION_IDENTIFIER_PATH_PARAMETER);
        return new SortableIdentifier(identifierString);
    }
    
    private static SortableIdentifier extractTicketIdentifierFromPath(RequestInfo requestInfo) {
        return new SortableIdentifier(requestInfo.getPathParameter(TicketUtils.TICKET_IDENTIFIER_PATH_PARAMETER));
    }
    
    @Override
    protected Integer getSuccessStatusCode(Void input, TicketDto output) {
        return HTTP_OK;
    }
}
