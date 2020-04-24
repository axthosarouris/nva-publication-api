package no.unit.nva.publication;

import com.fasterxml.jackson.databind.JsonNode;
import no.unit.nva.publication.exception.InputException;
import nva.commons.exceptions.ApiGatewayException;
import nva.commons.handlers.RequestInfo;

import java.util.UUID;

public final class RequestUtil {

    public static final String IDENTIFIER = "identifier";
    public static final String MISSING_AUTHORIZATION_IN_HEADERS = "Missing Authorization in Headers";
    public static final String IDENTIFIER_IS_NOT_A_VALID_UUID = "Identifier is not a valid UUID: ";
    public static final String AUTHORIZER_CLAIMS = "/authorizer/claims/";
    public static final String CUSTOM_FEIDE_ID = "custom:feideId";
    public static final String CUSTOM_ORG_NUMBER = "custom:orgNumber";
    public static final String MISSING_CLAIM_IN_REQUEST_CONTEXT =
            "Missing claim in requestContext: ";

    private RequestUtil() {
    }

    /**
     * Get identifier from request path parameters.
     *
     * @param requestInfo   requestInfo
     * @return  the identifier
     * @throws ApiGatewayException  exception thrown if value is missing
     */
    public static UUID getIdentifier(RequestInfo requestInfo) throws ApiGatewayException {
        String identifier = null;
        try {
            identifier = requestInfo.getPathParameters().get(IDENTIFIER);
            return UUID.fromString(identifier);
        } catch (Exception e) {
            throw new InputException(IDENTIFIER_IS_NOT_A_VALID_UUID + identifier, e);
        }
    }

    /**
     * Get orgNumber from requestContext authorizer claims.
     *
     * @param requestInfo   requestInfo.
     * @return  the orgNumber
     * @throws ApiGatewayException  exception thrown if value is missing
     */
    public static String getOrgNumber(RequestInfo requestInfo) throws ApiGatewayException {
        JsonNode jsonNode = requestInfo.getRequestContext().at(AUTHORIZER_CLAIMS + CUSTOM_ORG_NUMBER);
        if (!jsonNode.isMissingNode()) {
            return jsonNode.textValue();
        }
        throw new InputException(MISSING_CLAIM_IN_REQUEST_CONTEXT + CUSTOM_ORG_NUMBER, null);
    }

    /**
     * Get owner from requestContext authorizer claims.
     *
     * @param requestInfo   requestInfo.
     * @return  the owner
     * @throws ApiGatewayException  exception thrown if value is missing
     */
    public static String getOwner(RequestInfo requestInfo) throws ApiGatewayException {
        JsonNode jsonNode = requestInfo.getRequestContext().at(AUTHORIZER_CLAIMS + CUSTOM_FEIDE_ID);
        if (!jsonNode.isMissingNode()) {
            return jsonNode.textValue();
        }
        throw new InputException(MISSING_CLAIM_IN_REQUEST_CONTEXT + CUSTOM_FEIDE_ID, null);
    }

}
