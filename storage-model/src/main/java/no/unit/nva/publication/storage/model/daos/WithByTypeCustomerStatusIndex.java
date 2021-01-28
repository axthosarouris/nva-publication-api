package no.unit.nva.publication.storage.model.daos;

import static no.unit.nva.publication.storage.model.DatabaseConstants.BY_TYPE_CUSTOMER_STATUS_INDEX_PARTITION_KEY_NAME;
import static no.unit.nva.publication.storage.model.DatabaseConstants.BY_TYPE_CUSTOMER_STATUS_INDEX_SORT_KEY_NAME;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ComparisonOperator;
import com.amazonaws.services.dynamodbv2.model.Condition;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

public interface WithByTypeCustomerStatusIndex {

    @JsonProperty(BY_TYPE_CUSTOMER_STATUS_INDEX_PARTITION_KEY_NAME)
    String getByTypeCustomerStatusPartitionKey();

    default void setByTypeCustomerStatusPartitionKey(String byTypeCustomerStatusPartitionKey) {
        //Do nothing
    }

    @JsonProperty(BY_TYPE_CUSTOMER_STATUS_INDEX_SORT_KEY_NAME)
    String getByTypeCustomerStatusSortKey();

    default void setByTypeCustomerStatusSortKey(String byTypeCustomerStatusSk) {
        // do nothing
    }

    /**
     * Returns a Map of field-name:Condition for the key of the By-Type-Customer-Status index table. It's intended use
     * is primarily to get one specific item (by query) if one has the identifier of the entry. It provides an
     * alternative read access pattern to entries based on their Status.
     *
     * <p>Example:
     *
     * <p>{@code
     * new QueryRequest() .withTableName(RESOURCES_TABLE_NAME) .withIndexName(BY_TYPE_CUSTOMER_STATUS_INDEX_NAME)
     * .withKeyConditions(dao.byTypeCustomerStatusKey());
     * <p>
     * }
     *
     * @return a Map with field-name:Condition pair.
     */
    default Map<String, Condition> byTypeCustomerStatusKey() {
        Condition partitionKeyCondition = equalityIndexKeyCondition(getByTypeCustomerStatusPartitionKey());
        Condition sortKeyCondition = equalityIndexKeyCondition(getByTypeCustomerStatusSortKey());
        return
            Map.of(
                BY_TYPE_CUSTOMER_STATUS_INDEX_PARTITION_KEY_NAME, //key
                partitionKeyCondition, //value
                BY_TYPE_CUSTOMER_STATUS_INDEX_SORT_KEY_NAME,  //key
                sortKeyCondition //value
            );
    }

    private static Condition equalityIndexKeyCondition(String keyValue) {
        return new Condition()
            .withAttributeValueList(new AttributeValue(keyValue))
            .withComparisonOperator(ComparisonOperator.EQ);
    }
}
