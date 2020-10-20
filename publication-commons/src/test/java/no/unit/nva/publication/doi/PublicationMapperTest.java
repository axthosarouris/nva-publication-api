package no.unit.nva.publication.doi;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.javafaker.Faker;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import no.unit.nva.publication.doi.dto.Contributor;
import no.unit.nva.publication.doi.dto.PublicationDate;
import no.unit.nva.publication.doi.dto.PublicationStreamRecordTestDataGenerator;
import no.unit.nva.publication.doi.dto.PublicationType;
import no.unit.nva.publication.doi.dynamodb.dao.Identity;
import nva.commons.utils.IoUtils;
import nva.commons.utils.JsonUtils;
import org.junit.jupiter.api.Test;

class PublicationMapperTest {
    public static final String EXAMPLE_PREFIX = "http://example.net/nva/publication/";
    public static final String UNKNOWN_DYNAMODB_STREAMRECORD_TYPE = "UnknownType";

    private static final Faker FAKER = new Faker();
    private static final ObjectMapper objectMapper = JsonUtils.objectMapper;
    private static final Faker faker = new Faker();
    private static final PublicationDate EXAMPLE_PUBLICATION_DATE = new PublicationDate("1999", null, null);
    private static final String EXAMPLE_IDENTIFIER = "654321";
    private static final URI EXAMPLE_ID = URI.create(EXAMPLE_PREFIX + EXAMPLE_IDENTIFIER);
    private static final PublicationType EXAMPLE_PUBLICATION_TYPE = PublicationType.JOURNAL_ARTICLE;
    private static final String EXAMPLE_PUBLICATION_MAIN_TITLE = "Conformality loss and quantum criticality in "
        + "topological Higgs electrodynamics in 2+1 dimensions";
    private static final URI EXAMPLE_DOI = URI.create("https://doi.org/10.1103/physrevd.100.085005");
    private static final URI EXAMPLE_INSTITUTION_OWNER = URI.create(
        "https://api.dev.nva.aws.unit.no/customer/f54c8aa9-073a-46a1-8f7c-dde66c853934");
    private static final String EXAMPLE_CONTRIBUTOR_NAME = "Nogueira, Flavio S.";
    private static final String EXAMPLE_CONTRIBUTOR_ARPID = "451001";

    private String getExampleResource(String resource) {
        return IoUtils.stringFromResources(Path.of(resource));
    }

    private PublicationStreamRecordTestDataGenerator generateDynamoDbWithoutContributorIdentityNames() {
        return PublicationStreamRecordTestDataGenerator.Builder.createValidPublication(FAKER)
            .withContributorIdentities(getContributorIdentities(true))
            .build();
    }

    private List<Identity> getContributorIdentities(boolean withoutName) {
        List<Identity> contributors = new ArrayList<>();
        for (int i = 0; i < faker.random().nextInt(1, 10); i++) {
            Identity.Builder builder = new Identity.Builder();
            builder.withArpId(faker.number().digits(10));
            builder.withOrcId(faker.number().digits(10));
            builder.withName(withoutName ? null : faker.superhero().name());
            contributors.add(builder.build());
        }
        return contributors;
    }

    private ArrayList<URI> getIdOptions() {
        ArrayList<URI> idOptions = new ArrayList<>();
        idOptions.add(URI.create("https://example.net/foo/" + UUID.randomUUID().toString()));
        idOptions.add(null);
        return idOptions;
    }

    @Test
    void fromDynamodbStreamRecord() throws IOException {
        var publication = new PublicationMapper().fromDynamodbStreamRecord(EXAMPLE_PREFIX,
            getExampleResource(
                "doi/streamRecordTemplate.json"));
        assertThat(publication.getId(), is(equalTo(EXAMPLE_ID)));
        assertThat(publication.getType(), is(equalTo(EXAMPLE_PUBLICATION_TYPE)));
        assertThat(publication.getMainTitle(), is(equalTo(EXAMPLE_PUBLICATION_MAIN_TITLE)));
        assertThat(publication.getPublicationDate(), is(equalTo(EXAMPLE_PUBLICATION_DATE)));
        assertThat(publication.getDoi(), is(equalTo(EXAMPLE_DOI)));
        assertThat(publication.getInstitutionOwner(), is(equalTo(EXAMPLE_INSTITUTION_OWNER)));
        assertThat(publication.getContributor(), hasSize(1));
        assertThat(publication.getContributor(),
            hasItem(new Contributor(null, EXAMPLE_CONTRIBUTOR_ARPID, EXAMPLE_CONTRIBUTOR_NAME)));
    }

    @Test
    void fromDynamodbStreamRecordThrowsIllegalArgumentExceptionWhenUnknownDynamodbTableType() throws IOException {
        var rootNode = objectMapper.createObjectNode();
        rootNode.putObject("detail")
            .putObject("dynamodb")
            .putObject("newImage")
            .putObject("type")
            .put("s", UNKNOWN_DYNAMODB_STREAMRECORD_TYPE);
        var actualException = assertThrows(IllegalArgumentException.class, () ->
            new PublicationMapper().fromDynamodbStreamRecord(EXAMPLE_PREFIX,
                objectMapper.writeValueAsString(rootNode)));
        assertThat(actualException.getMessage(), containsString(PublicationMapper.ERROR_MUST_BE_PUBLICATION_TYPE));
    }

    @Test
    void fromDynamodbStreamRecordWhenContributorWithoutNameThenIsSkipped() throws IOException {
        var dynamodbStreamRecord = generateDynamoDbWithoutContributorIdentityNames()
            .asDynamoDbStreamRecord();
        var publication = new PublicationMapper().fromDynamodbStreamRecord(EXAMPLE_PREFIX,
            objectMapper.writeValueAsString(dynamodbStreamRecord));
        assertThat(publication.getContributor(), hasSize(0));
    }
}