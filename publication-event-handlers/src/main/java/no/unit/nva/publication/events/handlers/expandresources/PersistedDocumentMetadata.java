package no.unit.nva.publication.events.handlers.expandresources;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;
import no.unit.nva.expansion.model.ExpandedDatabaseEntry;
import no.unit.nva.expansion.model.ExpandedDoiRequest;
import no.unit.nva.expansion.model.ExpandedMessage;
import no.unit.nva.expansion.model.ExpandedResource;
import no.unit.nva.identifiers.SortableIdentifier;
import nva.commons.core.JacocoGenerated;

public class PersistedDocumentMetadata {

    public static final String RESOURCES_INDEX = "resources";
    public static final String MESSAGES_INDEX = "messages";
    public static final String DOI_REQUESTS_INDEX = "doirequests";

    public static final String INDEX_FIELD = "index";
    public static final String DOCUMENT_IDENTIFIER = "documentIdentifier";
    public static final String UNSUPPORTED_TYPE_ERROR_MESSAGE = "Currently unsupported type of entry:";
    @JsonProperty(INDEX_FIELD)
    private final String index;
    @JsonProperty(DOCUMENT_IDENTIFIER)
    private final SortableIdentifier documentIdentifier;

    @JsonCreator
    public PersistedDocumentMetadata(@JsonProperty(INDEX_FIELD) String index,
                                     @JsonProperty(DOCUMENT_IDENTIFIER) SortableIdentifier documentIdentifier) {
        this.index = index;
        this.documentIdentifier = documentIdentifier;
    }

    public static PersistedDocumentMetadata createMetadata(ExpandedDatabaseEntry expandedEntry) {
        if (expandedEntry instanceof ExpandedResource) {
            return new PersistedDocumentMetadata(RESOURCES_INDEX, expandedEntry.fetchIdentifier());
        } else if (expandedEntry instanceof ExpandedDoiRequest) {
            return new PersistedDocumentMetadata(DOI_REQUESTS_INDEX, expandedEntry.fetchIdentifier());
        } else if (expandedEntry instanceof ExpandedMessage) {
            return new PersistedDocumentMetadata(MESSAGES_INDEX, expandedEntry.fetchIdentifier());
        }
        throw new UnsupportedOperationException(
            UNSUPPORTED_TYPE_ERROR_MESSAGE + expandedEntry.getClass().getSimpleName());
    }

    public SortableIdentifier getDocumentIdentifier() {
        return documentIdentifier;
    }

    public String getIndex() {
        return index;
    }

    @JacocoGenerated
    @Override
    public int hashCode() {
        return Objects.hash(getIndex(), getDocumentIdentifier());
    }

    @JacocoGenerated
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PersistedDocumentMetadata)) {
            return false;
        }
        PersistedDocumentMetadata that = (PersistedDocumentMetadata) o;
        return Objects.equals(getIndex(), that.getIndex()) && Objects.equals(getDocumentIdentifier(),
                                                                             that.getDocumentIdentifier());
    }
}
