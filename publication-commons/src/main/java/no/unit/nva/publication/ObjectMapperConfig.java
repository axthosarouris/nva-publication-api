package no.unit.nva.publication;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import nva.commons.utils.JacocoGenerated;
import nva.commons.utils.JsonUtils;

public class ObjectMapperConfig {

    public static final ObjectMapper objectMapper;

    @JacocoGenerated
    private ObjectMapperConfig() {};

    static {
        objectMapper = JsonUtils.jsonParser;
        objectMapper.registerModule(new JavaTimeModule());
    }

}
