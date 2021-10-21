package no.unit.nva.publication.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import nva.commons.core.Environment;
import nva.commons.core.JsonUtils;

public final class PublicationEventsConfig {

    public static final ObjectMapper dynamoImageSerializerRemovingEmptyFields = JsonUtils.dynamoObjectMapper;
    public static Environment ENVIRONMENT = new Environment();

    private PublicationEventsConfig() {

    }
}
