package no.unit.nva.publication.service.impl;

import static java.util.Objects.isNull;
import static java.util.Objects.requireNonNull;
import static no.unit.nva.model.PublicationStatus.DRAFT_FOR_DELETION;
import static org.slf4j.LoggerFactory.getLogger;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Index;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.ItemCollection;
import com.amazonaws.services.dynamodbv2.document.QueryOutcome;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.amazonaws.services.dynamodbv2.document.spec.QuerySpec;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import no.unit.nva.identifiers.SortableIdentifier;
import no.unit.nva.model.Publication;
import no.unit.nva.model.PublicationStatus;
import no.unit.nva.model.exceptions.InvalidPublicationStatusTransitionException;
import no.unit.nva.publication.exception.DynamoDBException;
import no.unit.nva.publication.exception.InputException;
import no.unit.nva.publication.exception.InvalidPublicationException;
import no.unit.nva.publication.exception.NotImplementedException;
import no.unit.nva.publication.model.PublicationSummary;
import no.unit.nva.publication.model.PublishPublicationStatusResponse;
import no.unit.nva.publication.service.PublicationService;
import nva.commons.apigateway.exceptions.ApiGatewayException;
import nva.commons.apigateway.exceptions.NotFoundException;
import nva.commons.core.Environment;
import nva.commons.core.JacocoGenerated;
import org.apache.http.HttpStatus;

@SuppressWarnings("PMD.GodClass")
public class DynamoDBPublicationService implements PublicationService {

    public static final String IDENTIFIER = "identifier";
    public static final String MODIFIED_DATE = "modifiedDate";
    public static final String TABLE_NAME_ENV = "TABLE_NAME";
    public static final String BY_PUBLISHER_INDEX_NAME_ENV = "BY_PUBLISHER_INDEX_NAME";
    public static final String DYNAMODB_KEY_DELIMITER = "#";
    public static final String ERROR_READING_FROM_TABLE = "Error reading from Table";
    public static final String ERROR_WRITING_TO_TABLE = "Error writing to Table";
    public static final String PUBLICATION_NOT_FOUND = "Publication not found: ";
    public static final String ERROR_MAPPING_ITEM_TO_PUBLICATION = "Error mapping Item to Publication";
    public static final String ERROR_MAPPING_PUBLICATION_TO_ITEM = "Error mapping Publication to Item";
    public static final String IDENTIFIERS_NOT_EQUAL = "Identifier in request parameters '%s' "
                                                       + "is not equal to identifier in customer object '%s'";
    public static final String PUBLISH_IN_PROGRESS = "Publication is being published. This may take a while.";
    public static final String PUBLISH_COMPLETED = "Publication is published.";
    public static final String BY_PUBLISHED_PUBLICATIONS_INDEX_NAME = "BY_PUBLISHED_PUBLICATIONS_INDEX_NAME";
    public static final String DELETION_ERROR = "Not allowed to delete Publication not in DRAFT status";
    public static final String ERROR_UPDATING_STATUS = "Error updating status";

    private final ObjectMapper objectMapper;
    private final Table table;
    private final Index byPublisherIndex;
    private final Index byPublishedDateIndex;

    /**
     * Constructor for DynamoDBPublicationService.
     */
    public DynamoDBPublicationService(ObjectMapper objectMapper,
                                      Table table, Index byPublisherIndex, Index byPublishedDateIndex) {
        this.objectMapper = objectMapper;
        this.table = table;
        this.byPublisherIndex = byPublisherIndex;
        this.byPublishedDateIndex = byPublishedDateIndex;
    }

    /**
     * Constructor for DynamoDBPublicationService.
     */
    public DynamoDBPublicationService(AmazonDynamoDB client, ObjectMapper objectMapper, Environment environment) {
        String tableName = environment.readEnv(TABLE_NAME_ENV);
        String byPublisherIndexName = environment.readEnv(BY_PUBLISHER_INDEX_NAME_ENV);
        DynamoDB dynamoDB = new DynamoDB(client);
        this.objectMapper = objectMapper;
        this.table = dynamoDB.getTable(tableName);
        this.byPublisherIndex = table.getIndex(byPublisherIndexName);
        String byPublishedPublicationsIndex = environment.readEnv(BY_PUBLISHED_PUBLICATIONS_INDEX_NAME);
        this.byPublishedDateIndex = table.getIndex(byPublishedPublicationsIndex);
    }

    @Override
    public Publication createPublication(Publication publication) throws ApiGatewayException {
        try {
            //TODO: set identifier in PublicationMapper.newPublication(...)
            publication.setIdentifier(new SortableIdentifier(UUID.randomUUID().toString()));
            Item item = publicationToItem(publication);
            table.putItem(item);
        } catch (Exception e) {
            throw new DynamoDBException(ERROR_WRITING_TO_TABLE, e);
        }
        return publication;
    }

    @Override
    public Publication getPublication(SortableIdentifier identifier) throws ApiGatewayException {
        Item item = null;
        try {
            QuerySpec spec = new QuerySpec()
                .withHashKey(IDENTIFIER, identifier.toString())
                .withScanIndexForward(false)
                .withMaxResultSize(1);
            ItemCollection<QueryOutcome> outcomeItemCollection = table.query(spec);
            Iterator<Item> iterator = outcomeItemCollection.iterator();
            if (iterator.hasNext()) {
                item = outcomeItemCollection.iterator().next();
            }
        } catch (Exception e) {
            throw new DynamoDBException(ERROR_READING_FROM_TABLE, e);
        }
        if (item == null) {
            throw new NotFoundException(PUBLICATION_NOT_FOUND + identifier.toString());
        }
        return itemToPublication(item);
    }

    @Override
    public Publication updatePublication(SortableIdentifier identifier, Publication publication)
        throws ApiGatewayException {
        validateIdentifier(identifier, publication);
        try {
            publication.setModifiedDate(Instant.now());
            Item item = publicationToItem(publication);
            table.putItem(item);
        } catch (Exception e) {
            throw new DynamoDBException(ERROR_WRITING_TO_TABLE, e);
        }
        return publication;
    }

    @JacocoGenerated
    @Override
    public List<PublicationSummary> getPublicationsByPublisher(URI publisherId)
        throws ApiGatewayException {
        throw new NotImplementedException();
    }

    @Override
    public List<PublicationSummary> getPublicationsByOwner(String owner, URI publisherId)
        throws ApiGatewayException {
        allFieldsAreNonNull(owner, publisherId);

        String publisherOwner = String.join(DYNAMODB_KEY_DELIMITER, publisherId.toString(), owner);

        Map<String, String> nameMap = Map.of(
            "#publisherId", "publisherId",
            "#publisherOwnerDate", "publisherOwnerDate");
        Map<String, Object> valueMap = Map.of(
            ":publisherId", publisherId.toString(),
            ":publisherOwner", publisherOwner);

        QuerySpec querySpec = new QuerySpec()
            .withKeyConditionExpression(
                "#publisherId = :publisherId and begins_with(#publisherOwnerDate, :publisherOwner)")
            .withNameMap(nameMap)
            .withValueMap(valueMap);

        ItemCollection<QueryOutcome> items;
        try {
            items = byPublisherIndex.query(querySpec);
        } catch (Exception e) {
            throw new DynamoDBException(ERROR_READING_FROM_TABLE, e);
        }

        List<PublicationSummary> publications = parseJsonToPublicationSummaries(items);
        return filterOutOlderVersionsOfPublications(publications);
    }

    @Override
    public List<PublicationSummary> listPublishedPublicationsByDate(int pageSize) throws ApiGatewayException {

        List<PublicationSummary> publications = getPublicationSummaries(PublicationStatus.PUBLISHED, pageSize);

        return filterOutOlderVersionsOfPublications(publications);
    }

    @Override
    public PublishPublicationStatusResponse publishPublication(SortableIdentifier identifier)
        throws ApiGatewayException {
        Publication publicationToPublish = getPublication(identifier);
        if (isPublished(publicationToPublish)) {
            return new PublishPublicationStatusResponse(PUBLISH_COMPLETED, HttpStatus.SC_NO_CONTENT);
        } else {
            validatePublication(publicationToPublish);
            setPublishedProperties(publicationToPublish);
            updatePublication(identifier, publicationToPublish);
            return new PublishPublicationStatusResponse(PUBLISH_IN_PROGRESS, HttpStatus.SC_ACCEPTED);
        }
    }

    @Override
    public void markPublicationForDeletion(SortableIdentifier identifier, String owner) throws ApiGatewayException {
        Publication publication = getPublicationForOwner(identifier, owner);
        updateStatusForDeletion(publication);
        updatePublication(identifier, publication);
    }

    @Override
    public void deleteDraftPublication(SortableIdentifier identifier) throws ApiGatewayException {
        Publication publication = getPublication(identifier);
        if (DRAFT_FOR_DELETION.equals(publication.getStatus())) {
            for (PublicationSummary publicationSummary : getPublicationSummaries(identifier)) {
                deleteSinglePublication(publicationSummary);
            }
        }
    }

    protected static List<PublicationSummary> filterOutOlderVersionsOfPublications(
        List<PublicationSummary> publications) {
        return publications.stream()
            .collect(groupByIdentifer())
            .entrySet()
            .parallelStream()
            .flatMap(DynamoDBPublicationService::pickNewestVersion)
            .collect(Collectors.toList());
    }

    protected Publication itemToPublication(Item item) throws ApiGatewayException {
        Publication publicationOutcome;
        try {
            publicationOutcome = objectMapper.readValue(item.toJSON(), Publication.class);
        } catch (Exception e) {
            throw new DynamoDBException(ERROR_MAPPING_ITEM_TO_PUBLICATION, e);
        }
        return publicationOutcome;
    }

    protected Item publicationToItem(Publication publication) throws ApiGatewayException {
        Item item;
        try {
            item = Item.fromJSON(objectMapper.writeValueAsString(publication));
        } catch (JsonProcessingException e) {
            throw new InputException(ERROR_MAPPING_PUBLICATION_TO_ITEM, e);
        }
        return item;
    }

    protected Optional<PublicationSummary> toPublicationSummary(Item item) {
        try {
            PublicationSummary publicationSummary;
            publicationSummary = objectMapper.readValue(item.toJSON(), PublicationSummary.class);
            return Optional.of(publicationSummary);
        } catch (JsonProcessingException e) {
            System.out.println(e.getMessage());
            return Optional.empty();
        }
    }

    private static Collector<PublicationSummary, ?, Map<UUID, List<PublicationSummary>>> groupByIdentifer() {
        return Collectors.groupingBy(PublicationSummary::getIdentifier);
    }

    private static Stream<PublicationSummary> pickNewestVersion(Map.Entry<UUID, List<PublicationSummary>> group) {
        List<PublicationSummary> publications = group.getValue();
        Optional<PublicationSummary> mostRecent = publications.stream()
            .max(Comparator.comparing(
                PublicationSummary::getModifiedDate));
        return mostRecent.stream();
    }

    private void validateIdentifier(SortableIdentifier identifier, Publication publication) throws ApiGatewayException {
        if (!identifier.equals(publication.getIdentifier())) {
            throw new InputException(
                String.format(IDENTIFIERS_NOT_EQUAL, identifier, publication.getIdentifier()), null);
        }
    }

    private List<PublicationSummary> getPublicationSummaries(PublicationStatus status, int pageSize)
        throws DynamoDBException {
        Map<String, String> nameMap = Map.of("#status", "status");
        Map<String, Object> valueMap = Map.of(":status", status.getValue());

        QuerySpec querySpec = new QuerySpec()
            .withKeyConditionExpression("#status = :status")
            .withNameMap(nameMap)
            .withValueMap(valueMap)
            .withMaxPageSize(pageSize)
            .withScanIndexForward(false)
            .withMaxResultSize(pageSize);

        ItemCollection<QueryOutcome> items;
        try {
            items = byPublishedDateIndex.query(querySpec);
        } catch (Exception e) {
            getLogger(DynamoDBPublicationService.class).info(e.getMessage(), e);
            throw new DynamoDBException(ERROR_READING_FROM_TABLE, e);
        }

        List<PublicationSummary> publications = parseJsonToPublicationSummaries(items);
        return publications;
    }

    //TODO: remove when publications table no longer uses versioning
    private List<PublicationSummary> getPublicationSummaries(SortableIdentifier identifier) throws DynamoDBException {
        ItemCollection<QueryOutcome> items;
        try {
            items = table.query(IDENTIFIER, identifier.toString());
        } catch (Exception e) {
            getLogger(DynamoDBPublicationService.class).info(e.getMessage(), e);
            throw new DynamoDBException(ERROR_READING_FROM_TABLE, e);
        }

        List<PublicationSummary> publications = parseJsonToPublicationSummaries(items);
        return publications;
    }

    private List<PublicationSummary> parseJsonToPublicationSummaries(ItemCollection<QueryOutcome> items) {
        List<PublicationSummary> publications = new ArrayList<>();
        items.forEach(item -> toPublicationSummary(item).ifPresent(publications::add));
        return publications;
    }

    private void allFieldsAreNonNull(String owner, URI publisherId) {
        requireNonNull(owner);
        requireNonNull(publisherId);
    }

    private void setPublishedProperties(Publication publicationToPublish) {
        publicationToPublish.setStatus(PublicationStatus.PUBLISHED);
        publicationToPublish.setPublishedDate(Instant.now());
    }

    private void validatePublication(Publication publication) throws ApiGatewayException {
        List<String> missingFields = PublishPublicationValidator.validate(publication);
        if (!missingFields.isEmpty()) {
            throw new InvalidPublicationException(missingFields);
        }
    }

    private boolean isPublished(Publication publication) {
        return PublicationStatus.PUBLISHED.equals(publication.getStatus());
    }

    private Publication getPublicationForOwner(SortableIdentifier identifier, String owner) throws ApiGatewayException {
        Publication publication = getPublication(identifier);
        if (publication.getOwner().equals(owner)) {
            return publication;
        }
        throw new NotFoundException(PUBLICATION_NOT_FOUND + publication.getIdentifier().toString());
    }

    private void updateStatusForDeletion(Publication publication) throws ApiGatewayException {
        if (PublicationStatus.DRAFT.equals(publication.getStatus())) {
            try {
                publication.updateStatus(DRAFT_FOR_DELETION);
            } catch (InvalidPublicationStatusTransitionException e) {
                throw new InputException(ERROR_UPDATING_STATUS, e);
            }
        } else {
            throw new NotImplementedException();
        }
    }

    private void deleteSinglePublication(PublicationSummary publicationSummary) throws DynamoDBException {
        try {
            table.deleteItem(
                IDENTIFIER, publicationSummary.getIdentifier().toString(),
                MODIFIED_DATE, publicationSummary.getModifiedDate().toString()
            );
        } catch (Exception e) {
            throw new DynamoDBException(ERROR_WRITING_TO_TABLE, e);
        }
    }

    public static class PublishPublicationValidator {

        public static final String LINK_OR_FILE = "link or file";
        public static final String MAIN_TITLE = "main title";

        private PublishPublicationValidator() {
        }

        /**
         * Validate that Publication has required fields for publishing: main title, owner and link or file. Note: Would
         * have validated owner if it was not already required by an index in the DynamoDB table.
         *
         * @param publication the publication to validate
         * @return a list of missing fields
         */
        public static List<String> validate(Publication publication) {
            List<String> missingFields = new ArrayList<>();
            if (isNull(publication.getEntityDescription().getMainTitle())) {
                missingFields.add(MAIN_TITLE);
            }
            if (isNull(publication.getLink()) && emptyFiles(publication)) {
                missingFields.add(LINK_OR_FILE);
            }
            return missingFields;
        }

        private static boolean emptyFiles(Publication publication) {
            if (publication.getFileSet() == null || publication.getFileSet().getFiles() == null) {
                return true;
            } else {
                return publication.getFileSet().getFiles().isEmpty();
            }
        }
    }
}
