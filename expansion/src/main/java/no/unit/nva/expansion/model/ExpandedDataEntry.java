package no.unit.nva.expansion.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import no.unit.nva.identifiers.SortableIdentifier;
import nva.commons.core.JacocoGenerated;
import nva.commons.core.JsonSerializable;
import nva.commons.core.JsonUtils;

@JsonTypeInfo(use = Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(name = "Publication", value = ExpandedResource.class),
    @JsonSubTypes.Type(name = "DoiRequest", value = ExpandedDoiRequest.class),
    @JsonSubTypes.Type(name = "Message", value = ExpandedMessage.class),
    @JsonSubTypes.Type(name = "PublicationConversation", value = ExpandedResourceConversation.class),
})
public interface ExpandedDataEntry extends JsonSerializable {

    @JacocoGenerated
    @Override
    default String toJsonString() {
        try {
            return JsonUtils.dtoObjectMapper.writeValueAsString(this);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * An identifier that identifies the specific entry uniquely. It is meant to be used only
     * for persisting entries for usage from other services (such as the Search service or Analytics services)
     * @return a unique identifier.
     */
    SortableIdentifier identifyExpandedEntry();
}
