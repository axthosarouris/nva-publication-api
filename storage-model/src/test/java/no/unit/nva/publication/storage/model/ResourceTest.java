package no.unit.nva.publication.storage.model;

import static no.unit.nva.hamcrest.DoesNotHaveEmptyValues.doesNotHaveEmptyValues;
import static no.unit.nva.hamcrest.DoesNotHaveEmptyValues.doesNotHaveEmptyValuesIgnoringClasses;
import static nva.commons.core.attempt.Try.attempt;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsEqual.equalTo;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.MalformedURLException;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import no.unit.nva.identifiers.SortableIdentifier;
import no.unit.nva.model.Approval;
import no.unit.nva.model.ApprovalStatus;
import no.unit.nva.model.ApprovalsBody;
import no.unit.nva.model.Contributor;
import no.unit.nva.model.DoiRequest;
import no.unit.nva.model.EntityDescription;
import no.unit.nva.model.File;
import no.unit.nva.model.FileSet;
import no.unit.nva.model.Grant;
import no.unit.nva.model.Identity;
import no.unit.nva.model.Level;
import no.unit.nva.model.License;
import no.unit.nva.model.NameType;
import no.unit.nva.model.Organization;
import no.unit.nva.model.Publication;
import no.unit.nva.model.PublicationDate;
import no.unit.nva.model.PublicationStatus;
import no.unit.nva.model.Reference;
import no.unit.nva.model.ResearchProject;
import no.unit.nva.model.ResearchProject.Builder;
import no.unit.nva.model.Role;
import no.unit.nva.model.contexttypes.Journal;
import no.unit.nva.model.exceptions.InvalidIssnException;
import no.unit.nva.model.instancetypes.journal.JournalArticle;
import no.unit.nva.model.pages.Range;
import nva.commons.core.JsonUtils;
import org.javers.core.Javers;
import org.javers.core.JaversBuilder;
import org.javers.core.diff.Diff;
import org.junit.jupiter.api.Test;

public class ResourceTest {

    public static final String SOME_TITLE = "SomeTitle";
    public static final URI SAMPLE_ORG_URI = URI.create("https://www.example.com/123");
    public static final Organization SAMPLE_ORG = sampleOrganization();
    public static final URI SAMPLE_DOI = URI.create("http://doi.org/123-456");
    public static final Instant SAMPLE_APPROVAL_DATE = Instant.parse("2020-05-03T12:22:22.00Z");
    public static final String SOME_OWNER = "some@owner.no";
    public static final URI SOME_LINK = URI.create("https://example.org/somelink");
    public static final Instant EMBARGO_DATE = Instant.parse("2021-01-01T12:00:22.23Z");
    public static final URI SAMPLE_ID = URI.create("https://example.com/some/id");

    public static final Instant RESOURCE_CREATION_TIME = Instant.parse("1900-12-03T10:15:30.00Z");
    public static final Instant RESOURCE_MODIFICATION_TIME = Instant.parse("2000-01-03T00:00:18.00Z");
    public static final Instant RESOURCE_SECOND_MODIFICATION_TIME = Instant.parse("2010-01-03T02:00:25.00Z");
    public static final Instant RESOURCE_PUBLISHED_DATE = Instant.parse("2012-04-03T06:12:35.00Z");
    public static final Instant RESOURCE_INDEXED_TIME = Instant.parse("2013-05-03T12:22:22.00Z");
    public static final URI SAMPLE_LANGUAGE = URI.create("https://some.com/language");
    public static final String SAMPLE_ISSN = "2049-3630";

    public final DoiRequest EMPTY_DOI_REQUEST = new DoiRequest.Builder().build();


    private final FileSet SAMPLE_FILE_SET = sampleFileSet();
    private final List<ResearchProject> SAMPLE_PROJECTS = sampleProjects();
    private Javers javers = JaversBuilder.javers().build();



    @Test
    public void builderContainsAllFields() {
        Resource resource = sampleResource();
        assertThat(resource, doesNotHaveEmptyValues());
    }

    @Test
    public void copyContainsAllFields() {
        Resource resource = sampleResource();
        Resource copy = resource.copy().build();
        JsonNode resourceJson = JsonUtils.objectMapper.convertValue(resource, JsonNode.class);
        JsonNode copyJson = JsonUtils.objectMapper.convertValue(copy, JsonNode.class);
        assertThat(resource, doesNotHaveEmptyValues());
        assertThat(copy, is(equalTo(resource)));
        assertThat(resourceJson, is(equalTo(copyJson)));
    }

    @Test
    public void toDtoReturnsDtoWithoutLossOfInformation() {
        Resource resource = sampleResource();
        assertThat(resource, doesNotHaveEmptyValues());
        Publication publication = resource.toPublication();
        Resource fromPublication = Resource.fromPublication(publication);
        Diff diff = javers.compare(resource, fromPublication);
        assertThat(diff.prettyPrint(), diff.getChanges().size(), is(0));
    }

    @Test
    public void fromDTOtoDAOtoDTOReturnsDtoWithoutLossOfInformation()
        throws MalformedURLException, InvalidIssnException {
        Publication publication = samplePublication(sampleJournalArticleReference());
        assertThat(publication, doesNotHaveEmptyValuesIgnoringClasses(List.of(DoiRequest.class)));

        Publication fromDao= Resource.fromPublication(publication).toPublication();
        Diff diff = javers.compare(publication, fromDao);
        assertThat(diff.prettyPrint(), diff.getChanges().size(), is(0));
        assertThat(publication,is(equalTo(fromDao)));
    }





    private Publication samplePublication(Reference reference) {
        return new Publication.Builder()
            .withIdentifier(SortableIdentifier.next())
            .withCreatedDate(RESOURCE_CREATION_TIME)
            .withModifiedDate(RESOURCE_MODIFICATION_TIME)
            .withIndexedDate(RESOURCE_INDEXED_TIME)
            .withPublishedDate(RESOURCE_PUBLISHED_DATE)
            .withOwner(SOME_OWNER)
            .withPublisher(SAMPLE_ORG)
            .withDoi(SAMPLE_DOI)
            .withFileSet(SAMPLE_FILE_SET)
            .withHandle(randomUri())
            .withStatus(PublicationStatus.PUBLISHED)
            .withLink(SOME_LINK)
            .withProjects(SAMPLE_PROJECTS)
            .withDoiRequest(EMPTY_DOI_REQUEST)
            .withEntityDescription(sampleEntityDescription(reference))
            .build();
    }

    private static Identity sampleIdentity() {
        return new Identity.Builder()
            .withId(SAMPLE_ID)
            .withName(randomString())
            .withArpId(randomString())
            .withNameType(NameType.PERSONAL)
            .withOrcId(randomString())
            .build();
    }

    private static Organization sampleOrganization() {
        return new Organization.Builder()
            .withId(SAMPLE_ORG_URI)
            .withLabels(Map.of("labelKey", "labelValue"))
            .build();
    }

    private static String randomString() {
        return UUID.randomUUID().toString();
    }

    private EntityDescription sampleEntityDescription(Reference reference){
        Map<String, String> alternativeTitles = Map.of(randomString(), randomString());
        return new EntityDescription.Builder()
            .withDate(randomPublicationDate())
            .withAbstract(randomString())
            .withDescription(randomString())
            .withAlternativeTitles(alternativeTitles)
            .withContributors(sampleContributor())
            .withLanguage(SAMPLE_LANGUAGE)
            .withMainTitle(randomString())
            .withMetadataSource(randomUri())
            .withNpiSubjectHeading(randomString())
            .withTags(List.of(randomString()))
            .withReference(reference)

            .build();
    }

    private Reference sampleJournalArticleReference() throws InvalidIssnException, MalformedURLException {
        return new Reference.Builder()
            .withDoi(randomUri())
            .withPublishingContext(sampleJournalInstance())
            .withPublicationInstance(samlpeJournalArticle())
            .build();
    }

    private JournalArticle samlpeJournalArticle() {
        return new JournalArticle.Builder()
            .withPeerReviewed(true)
            .withArticleNumber(randomString())
            .withIssue(randomString())
            .withPages(new Range.Builder().withBegin(randomString()).withEnd(randomString()).build())
            .withVolume(randomString())
            .build();
    }

    private Journal sampleJournalInstance() throws InvalidIssnException, MalformedURLException {
        return new Journal.Builder()
            .withLevel(Level.LEVEL_2)
            .withOnlineIssn(SAMPLE_ISSN)
            .withTitle(randomString())
            .withOpenAccess(true)
            .withPeerReviewed(true)
            .withPrintIssn(SAMPLE_ISSN)
            .withUrl(randomUri().toURL())
            .build();
    }

    private PublicationDate randomPublicationDate() {
        return new PublicationDate.Builder().withDay(randomString())
            .withMonth(randomString())
            .withYear(randomString())
            .build();
    }

    private List<Contributor> sampleContributor() {
        Contributor contributor = attempt(() -> new Contributor.Builder()
            .withIdentity(sampleIdentity())
            .withEmail(randomString())
            .withAffiliations(List.of(SAMPLE_ORG))
            .withRole(Role.CREATOR)
            .withSequence(1)
            .build())
            .orElseThrow();
        return List.of(contributor);
    }

    private List<ResearchProject> sampleProjects() {
        Approval approval = new Approval.Builder()
            .withApprovalStatus(ApprovalStatus.APPLIED)
            .withApplicationCode("SomeApplicationCode")
            .withApprovedBy(ApprovalsBody.NMA)
            .withDate(SAMPLE_APPROVAL_DATE)
            .build();

        Grant grant = new Grant.Builder()
            .withId(randomString())
            .withSource("Some grant source")
            .build();
        ResearchProject researchProject = new Builder().withId(randomUri())
            .withApprovals(List.of(approval))
            .withGrants(List.of(grant))
            .withName(randomString())
            .build();

        return List.of(researchProject);
    }

    private FileSet sampleFileSet() {
        FileSet files = new FileSet();
        License license = new License.Builder()
            .withIdentifier(randomString())
            .withLabels(Map.of(randomString(), randomString()))
            .withLink(URI.create("https://www.example.com/sample/license/link"))
            .build();
        File file = new File.Builder()
            .withIdentifier(UUID.randomUUID())
            .withAdministrativeAgreement(true)
            .withIdentifier(UUID.randomUUID())
            .withEmbargoDate(EMBARGO_DATE)
            .withMimeType(randomString())
            .withSize(100L)
            .withLicense(license)
            .withName(randomString())
            .build();
        files.setFiles(List.of(file));
        return files;
    }

    private Resource sampleResource() {

        return Resource.builder()
            .withIdentifier(SortableIdentifier.next())
            .withTitle(SOME_TITLE)
            .withStatus(PublicationStatus.DRAFT)
            .withOwner(SOME_OWNER)
            .withCreatedDate(RESOURCE_CREATION_TIME)
            .withModifiedDate(RESOURCE_SECOND_MODIFICATION_TIME)
            .withPublishedDate(RESOURCE_MODIFICATION_TIME)
            .withIndexedDate(RESOURCE_PUBLISHED_DATE)
            .withPublisher(SAMPLE_ORG)
            .withFileSet(SAMPLE_FILE_SET)
            .withLink(SOME_LINK)
            .build();
    }

    private URI randomUri() {
        return URI.create("https://example.com/" + UUID.randomUUID().toString());
    }
}