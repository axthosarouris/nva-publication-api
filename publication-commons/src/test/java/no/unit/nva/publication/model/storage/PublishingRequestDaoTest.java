package no.unit.nva.publication.model.storage;

import static no.unit.nva.hamcrest.DoesNotHaveEmptyValues.doesNotHaveEmptyValues;
import static no.unit.nva.publication.model.business.StorageModelTestUtils.randomPublishingRequest;
import static no.unit.nva.publication.model.storage.DynamoEntry.parseAttributeValuesMap;
import static no.unit.nva.publication.storage.model.DatabaseConstants.CUSTOMER_INDEX_FIELD_PREFIX;
import static no.unit.nva.publication.storage.model.DatabaseConstants.KEY_FIELDS_DELIMITER;
import static no.unit.nva.publication.storage.model.DatabaseConstants.RESOURCE_INDEX_FIELD_PREFIX;
import static no.unit.nva.testutils.RandomDataGenerator.randomElement;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsEqual.equalTo;
import java.net.URI;
import java.time.Clock;
import no.unit.nva.identifiers.SortableIdentifier;
import no.unit.nva.model.Publication;
import no.unit.nva.model.testing.PublicationGenerator;
import no.unit.nva.publication.model.business.PublishingRequestCase;
import no.unit.nva.publication.model.business.Resource;
import no.unit.nva.publication.model.business.TicketStatus;
import no.unit.nva.publication.model.business.UserInstance;
import no.unit.nva.publication.service.ResourcesLocalTest;
import no.unit.nva.publication.service.impl.ResourceService;
import no.unit.nva.publication.service.impl.TicketService;
import nva.commons.apigateway.exceptions.ApiGatewayException;
import nva.commons.core.SingletonCollector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PublishingRequestDaoTest extends ResourcesLocalTest {
    
    private static final String SAMPLE_USER = "some@onwer";
    private static final String SAMPLE_CUSTOMER_IDENTIFIER = "somePublsherId";
    private static final URI SAMPLE_CUSTOMER = URI.create("https://some.example.org/" + SAMPLE_CUSTOMER_IDENTIFIER);
    private static final UserInstance SAMPLE_USER_INSTANCE = UserInstance.create(SAMPLE_USER, SAMPLE_CUSTOMER);
    private static final SortableIdentifier SAMPLE_RESOURCE_IDENTIFIER = SortableIdentifier.next();
    
    private ResourceService resourceService;
    private TicketService ticketService;
    
    @BeforeEach
    public void setup() {
        super.init();
        this.resourceService = new ResourceService(super.client, Clock.systemDefaultZone());
        this.ticketService = new TicketService(super.client);
    }
    
    @Test
    void shouldReturnObjectWithPartitionKeyContainingPublisherAndResourceId() {
        
        var queryObject =
            PublishingRequestDao.queryByCustomerAndResourceIdentifier(SAMPLE_USER_INSTANCE, SAMPLE_RESOURCE_IDENTIFIER);
        var expectedPartitionKey = CUSTOMER_INDEX_FIELD_PREFIX
                                   + KEY_FIELDS_DELIMITER
                                   + SAMPLE_CUSTOMER_IDENTIFIER
                                   + KEY_FIELDS_DELIMITER
                                   + RESOURCE_INDEX_FIELD_PREFIX
                                   + KEY_FIELDS_DELIMITER
                                   + SAMPLE_RESOURCE_IDENTIFIER;
        
        assertThat(queryObject.getByCustomerAndResourcePartitionKey(), is(equalTo(expectedPartitionKey)));
    }
    
    @Test
    void shouldReturnQueryObjectWithCompletePrimaryKey() {
        var sampleEntryIdentifier = SortableIdentifier.next();
        var queryObject =
            PublishingRequestCase.createQueryObject(UserInstance.create(SAMPLE_USER, SAMPLE_CUSTOMER),
                SortableIdentifier.next(),
                sampleEntryIdentifier);
        var queryDao = PublishingRequestDao.queryObject(queryObject);
    
        assertThat(queryDao.getPrimaryKeyPartitionKey(), is(equalTo(expectedPublicationRequestPrimaryPartitionKey())));
        assertThat(queryDao.getPrimaryKeySortKey(),
            is(equalTo(expectedPublicationRequestPrimarySortKey(sampleEntryIdentifier))));
    }
    
    @Test
    void shouldCreateDaoWithoutLossOfInformation() {
        var aprDao = sampleApprovePublicationRequestDao();
        assertThat(aprDao, doesNotHaveEmptyValues());
        var dynamoMap = aprDao.toDynamoFormat();
        var parsedDao = parseAttributeValuesMap(dynamoMap, aprDao.getClass());
        assertThat(parsedDao, is(equalTo(aprDao)));
    }
    
    @Test
    void shouldQueryForPublishingRequestBasedOnCustomerIdAndResourceIdentifier() throws ApiGatewayException {
        var publication = createPublication();
        var query = PublishingRequestDao.queryPublishingRequestByResource(publication.getPublisher().getId(),
            publication.getIdentifier());
    
        var publishingRequest = PublishingRequestCase.createOpeningCaseObject(publication);
        var persistedRequest = ticketService.createTicket(publishingRequest, PublishingRequestCase.class);
        var queryResult = client.query(query);
        var retrievedByPublicationIdentifier = queryResult.getItems().stream()
                                                   .map(item -> parseAttributeValuesMap(item,
                                                       PublishingRequestDao.class))
                                                   .map(PublishingRequestDao::getData)
                                                   .collect(SingletonCollector.collect());
        assertThat(retrievedByPublicationIdentifier, is(equalTo(persistedRequest)));
    }
    
    private static PublishingRequestDao sampleApprovePublicationRequestDao() {
        var publication = PublicationGenerator.randomPublication();
        var publishingRequestCase = randomPublishingRequest(publication).complete(publication);
        publishingRequestCase.setStatus(randomElement(TicketStatus.values()));
        return (PublishingRequestDao) publishingRequestCase.toDao();
    }
    
    private Publication createPublication() {
        var publication = PublicationGenerator.randomPublication();
        var userInstance = UserInstance.fromPublication(publication);
        return Resource.fromPublication(publication).persistNew(resourceService, userInstance);
    }
    
    private String expectedPublicationRequestPrimarySortKey(SortableIdentifier entryIdentifier) {
        return TicketDao.TICKETS_INDEXING_TYPE
               + KEY_FIELDS_DELIMITER
               + entryIdentifier.toString();
    }
    
    private String expectedPublicationRequestPrimaryPartitionKey() {
        return TicketDao.TICKETS_INDEXING_TYPE
               + KEY_FIELDS_DELIMITER
               + SAMPLE_CUSTOMER_IDENTIFIER
               + KEY_FIELDS_DELIMITER
               + SAMPLE_USER;
    }
}
