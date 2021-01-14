package no.unit.nva.publication.doi.dto;

import static nva.commons.core.JsonUtils.objectMapper;

import com.amazonaws.services.lambda.runtime.events.DynamodbEvent;
import com.github.javafaker.Faker;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import no.unit.nva.publication.doi.dto.PublicationStreamRecordTestDataGenerator.Builder;
import no.unit.nva.publication.doi.dynamodb.dao.DynamodbStreamRecordJsonPointers;
import no.unit.nva.publication.doi.dynamodb.dao.DynamodbStreamRecordJsonPointers.DynamodbImageType;

public class PublicationDynamoEventTestDataGenerator {

    protected final List<PublicationStreamRecordTestDataGenerator> records;
    private final Faker faker;
    private final DynamodbStreamRecordJsonPointers jsonPointers;

    /**
     * Default constructor for PublicationDynamoEventTestDataGenerator.
     */
    public PublicationDynamoEventTestDataGenerator() {
        this.records = new ArrayList<>();
        this.faker = new Faker();
        this.jsonPointers = new DynamodbStreamRecordJsonPointers(DynamodbImageType.NEW);
    }

    /**
     * Creates a stream record filled by faker.
     * @return PublicationDynamoEventTestDataGenerator with one additional stream record added to list of records.
     */
    public PublicationDynamoEventTestDataGenerator createRandomStreamRecord() {
        return createRandomStreamRecord(null);
    }

    /**
     * Creates a stream record by the provided builder, if none is provided it will use the faker to construct a
     * stream record with the builder.
     * @param  streamRecordBuilder Optional builder if you want to customize/specify test data.
     * @return PublicationDynamoEventTestDataGenerator with one additional stream record added to list of records.
     */
    public PublicationDynamoEventTestDataGenerator createRandomStreamRecord(
        PublicationStreamRecordTestDataGenerator.Builder streamRecordBuilder) {
        var builderToUse = Optional.ofNullable(streamRecordBuilder)
            .orElse(createTestdataGeneratorBuilderWithValidRecord());
        this.records.add(builderToUse.build());
        return this;
    }

    /**
     * Provides a DynamodbEvent object representation of the object.
     *
     * @return DynamodbEvent representation of the object.
     * @throws IOException thrown if the template files cannot be found.
     */
    public DynamodbEvent asDynamoDbEvent() {
        var root = objectMapper.createObjectNode();
        var records = root.putArray("records");
        this.records.forEach(p -> records.addPOJO(p.asDynamoDbStreamRecord()));
        return objectMapper.convertValue(root, DynamodbEvent.class);
    }

    /**
     * Clear the added stream records.
     */
    public void clearRecords() {
        this.records.clear();
    }

    private Builder createTestdataGeneratorBuilderWithValidRecord() {
        return PublicationStreamRecordTestDataGenerator.Builder.createValidPublication(faker, jsonPointers);
    }
}
