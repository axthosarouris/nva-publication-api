package no.unit.nva.publication.storage.model.daos;

import static no.unit.nva.publication.storage.model.DatabaseConstants.PRIMARY_KEY_PARTITION_KEY_NAME;
import static no.unit.nva.publication.storage.model.DatabaseConstants.PRIMARY_KEY_SORT_KEY_NAME;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import nva.commons.core.JacocoGenerated;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(IdentifierEntry.class),
    @JsonSubTypes.Type(UniqueDoiRequestEntry.class),
})
public abstract class UniquenessEntry implements DynamoEntry, WithPrimaryKey {

    private String partitionKey;
    private String sortKey;

    /*For JSON Jackson*/
    @JacocoGenerated
    public UniquenessEntry() {

    }

    public UniquenessEntry(String identifier) {
        this.partitionKey = getType() + identifier;
        this.sortKey = partitionKey;
    }

    @Override
    @JsonProperty(PRIMARY_KEY_PARTITION_KEY_NAME)
    public final String getPrimaryKeyPartitionKey() {
        return partitionKey;
    }

    @Override
    public final void setPrimaryKeyPartitionKey(String partitionKey) {
        this.partitionKey = partitionKey;
    }

    @Override
    @JsonProperty(PRIMARY_KEY_SORT_KEY_NAME)
    public final String getPrimaryKeySortKey() {
        return sortKey;
    }

    @Override
    public final void setPrimaryKeySortKey(String sortKey) {
        this.sortKey = sortKey;
    }

    protected abstract String getType();
}
