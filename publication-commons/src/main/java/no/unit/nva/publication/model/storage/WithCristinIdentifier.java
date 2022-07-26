package no.unit.nva.publication.model.storage;

import static no.unit.nva.publication.storage.model.DatabaseConstants.CRISTIN_IDENTIFIER_INDEX_FIELD_PREFIX;
import static no.unit.nva.publication.storage.model.DatabaseConstants.KEY_FIELDS_DELIMITER;
import static no.unit.nva.publication.storage.model.DatabaseConstants.RESOURCES_BY_CRISTIN_ID_INDEX_PARTITION_KEY_NAME;
import static no.unit.nva.publication.storage.model.DatabaseConstants.RESOURCES_BY_CRISTIN_ID_INDEX_SORT_KEY_NAME;
import static no.unit.nva.publication.storage.model.DatabaseConstants.RESOURCES_TABLE_NAME;
import static no.unit.nva.publication.storage.model.DatabaseConstants.RESOURCE_BY_CRISTIN_ID_INDEX_NAME;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ComparisonOperator;
import com.amazonaws.services.dynamodbv2.model.Condition;
import com.amazonaws.services.dynamodbv2.model.QueryRequest;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;
import java.util.Optional;
import no.unit.nva.publication.model.business.Entity;
import no.unit.nva.publication.model.business.RowLevelSecurity;

public interface WithCristinIdentifier<T extends RowLevelSecurity & Entity> extends DynamoEntryByIdentifier<T> {
    
    @JsonProperty(RESOURCES_BY_CRISTIN_ID_INDEX_PARTITION_KEY_NAME)
    default String getResourceByCristinIdentifierPartitionKey() {
        return getCristinIdentifier().isEmpty() ? null
                   : CRISTIN_IDENTIFIER_INDEX_FIELD_PREFIX + KEY_FIELDS_DELIMITER + getCristinIdentifier();
    }
    
    @JsonProperty(RESOURCES_BY_CRISTIN_ID_INDEX_SORT_KEY_NAME)
    default String getResourceByCristinIdentifierSortKey() {
        return getContainedDataType() + KEY_FIELDS_DELIMITER + getIdentifier();
    }
    
    @JsonIgnore
    Optional<String> getCristinIdentifier();
    
    default QueryRequest createQueryFindByCristinIdentifier() {
        return new QueryRequest()
            .withTableName(RESOURCES_TABLE_NAME)
            .withIndexName(RESOURCE_BY_CRISTIN_ID_INDEX_NAME)
            .withKeyConditions(createConditionsWithCristinIdentifier());
    }
    
    default Map<String, Condition> createConditionsWithCristinIdentifier() {
        Condition condition = new Condition()
            .withComparisonOperator(ComparisonOperator.EQ)
            .withAttributeValueList(new AttributeValue(getResourceByCristinIdentifierPartitionKey()));
        return Map.of(RESOURCES_BY_CRISTIN_ID_INDEX_PARTITION_KEY_NAME, condition);
    }
}
