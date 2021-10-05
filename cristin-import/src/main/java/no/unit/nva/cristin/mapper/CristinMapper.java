package no.unit.nva.cristin.mapper;

import static java.util.Objects.isNull;
import static no.unit.nva.cristin.lambda.constants.HardcodedValues.HARDCODED_SAMPLE_DOI;
import static no.unit.nva.cristin.lambda.constants.HardcodedValues.UNIT_CUSTOMER_ID;
import static no.unit.nva.cristin.lambda.constants.MappingConstants.HRCS_ACTIVITIES_MAP;
import static no.unit.nva.cristin.lambda.constants.MappingConstants.HRCS_CATEGORIES_MAP;
import static no.unit.nva.cristin.lambda.constants.MappingConstants.IGNORED_AND_POSSIBLY_EMPTY_PUBLICATION_FIELDS;
import static no.unit.nva.cristin.lambda.constants.MappingConstants.NVA_API_DOMAIN;
import static no.unit.nva.cristin.lambda.constants.MappingConstants.PATH_CUSTOMER;
import static no.unit.nva.cristin.mapper.CristinHrcsCategoriesAndActivities.HRCS_ACTIVITY_URI;
import static no.unit.nva.cristin.mapper.CristinHrcsCategoriesAndActivities.HRCS_CATEGORY_URI;
import static no.unit.nva.cristin.mapper.CristinMainCategory.isBook;
import static no.unit.nva.cristin.mapper.CristinMainCategory.isChapter;
import static no.unit.nva.cristin.mapper.CristinMainCategory.isEvent;
import static no.unit.nva.cristin.mapper.CristinMainCategory.isJournal;
import static no.unit.nva.cristin.mapper.CristinMainCategory.isReport;
import static no.unit.nva.cristin.mapper.CristinSecondaryCategory.isDegreeMaster;
import static no.unit.nva.cristin.mapper.CristinSecondaryCategory.isDegreePhd;
import static no.unit.nva.hamcrest.DoesNotHaveEmptyValues.doesNotHaveEmptyValuesIgnoringFields;
import static nva.commons.core.attempt.Try.attempt;
import static org.hamcrest.MatcherAssert.assertThat;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import java.net.URI;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import no.unit.nva.cristin.mapper.nva.CristinMappingModule;
import no.unit.nva.cristin.mapper.nva.NvaBookBuilder;
import no.unit.nva.cristin.mapper.nva.NvaDegreeBuilder;
import no.unit.nva.cristin.mapper.nva.NvaReportBuilder;
import no.unit.nva.model.AdditionalIdentifier;
import no.unit.nva.model.Contributor;
import no.unit.nva.model.EntityDescription;
import no.unit.nva.model.Organization;
import no.unit.nva.model.Publication;
import no.unit.nva.model.Publication.Builder;
import no.unit.nva.model.PublicationDate;
import no.unit.nva.model.PublicationStatus;
import no.unit.nva.model.Reference;
import no.unit.nva.model.ResearchProject;
import no.unit.nva.model.contexttypes.Chapter;
import no.unit.nva.model.contexttypes.Event;
import no.unit.nva.model.contexttypes.PublicationContext;
import no.unit.nva.model.exceptions.InvalidIsbnException;
import no.unit.nva.model.exceptions.InvalidIssnException;
import no.unit.nva.model.exceptions.InvalidUnconfirmedSeriesException;
import no.unit.nva.model.instancetypes.PublicationInstance;
import no.unit.nva.model.pages.Pages;
import no.unit.nva.publication.s3imports.UriWrapper;
import nva.commons.core.attempt.Try;
import nva.commons.core.language.LanguageMapper;
import nva.commons.doi.DoiConverter;
import nva.commons.doi.DoiValidator;

@SuppressWarnings({"PMD.GodClass", "PMD.CouplingBetweenObjects"})
public class CristinMapper extends CristinMappingModule {

    public static final String EMPTY_STRING = "";
    private static final Config config = loadConfiguration();
    private static final boolean VALIDATE_DOI_ONLINE = parseValidateDoiOnline();

    private final DoiConverter doiConverter;

    public CristinMapper(CristinObject cristinObject) {
        super(cristinObject);
        DoiValidator doiValidator = new DoiValidator();
        doiConverter = new DoiConverter(doiUri -> validateDoi(doiValidator, doiUri));
    }

    public Publication generatePublication() {
        Publication publication = new Builder()
            .withAdditionalIdentifiers(Set.of(extractIdentifier()))
            .withEntityDescription(generateEntityDescription())
            .withCreatedDate(extractEntryCreationDate())
            .withModifiedDate(extractEntryLastModifiedDate())
            .withPublishedDate(extractEntryCreationDate())
            .withPublisher(extractOrganization())
            .withOwner(cristinObject.getPublicationOwner())
            .withStatus(PublicationStatus.PUBLISHED)
            .withLink(HARDCODED_SAMPLE_DOI)
            .withProjects(extractProjects())
            .withSubjects(generateNvaHrcsCategoriesAndActivities())
            .build();
        assertPublicationDoesNotHaveEmptyFields(publication);
        return publication;
    }

    private static boolean validateDoi(DoiValidator doiValidator, URI doiUri) {
        return VALIDATE_DOI_ONLINE ? doiValidator.validateOnline(doiUri) : DoiValidator.validateOffline(doiUri);
    }

    private static boolean parseValidateDoiOnline() {
        return config.getBoolean("doi.validation.online");
    }

    private static Config loadConfiguration() {
        return ConfigFactory.load(ConfigFactory.defaultApplication());
    }

    private void assertPublicationDoesNotHaveEmptyFields(Publication publication) {
        try {
            assertThat(publication,
                       doesNotHaveEmptyValuesIgnoringFields(IGNORED_AND_POSSIBLY_EMPTY_PUBLICATION_FIELDS));
        } catch (Error error) {
            String message = error.getMessage();
            throw new MissingFieldsException(message);
        }
    }

    private List<ResearchProject> extractProjects() {
        if (cristinObject.getPresentationalWork() == null) {
            return null;
        }
        return cristinObject.getPresentationalWork()
            .stream()
            .filter(CristinPresentationalWork::isProject)
            .map(CristinPresentationalWork::toNvaResearchProject)
            .collect(Collectors.toList());
    }

    private Organization extractOrganization() {
        UriWrapper customerId = new UriWrapper(NVA_API_DOMAIN).addChild(PATH_CUSTOMER, UNIT_CUSTOMER_ID);
        return new Organization.Builder().withId(customerId.getUri()).build();
    }

    private Instant extractEntryCreationDate() {
        return Optional.ofNullable(cristinObject.getEntryCreationDate())
            .map(ld -> ld.atStartOfDay().toInstant(zoneOffset()))
            .orElse(null);
    }

    private Instant extractEntryLastModifiedDate() {
        return Optional.ofNullable(cristinObject.getEntryLastModifiedDate())
                .map(ld -> ld.atStartOfDay().toInstant(zoneOffset()))
                .orElseGet(() -> extractEntryCreationDate());
    }

    private ZoneOffset zoneOffset() {
        return ZoneOffset.UTC.getRules().getOffset(Instant.now());
    }

    private EntityDescription generateEntityDescription() {
        return new EntityDescription.Builder()
            .withLanguage(extractLanguage())
            .withMainTitle(extractMainTitle())
            .withDate(extractPublicationDate())
            .withReference(buildReference())
            .withContributors(extractContributors())
            .withNpiSubjectHeading(extractNpiSubjectHeading())
            .withAbstract(extractAbstract())
            .withTags(extractTags())
            .build();
    }

    private List<Contributor> extractContributors() {
        return cristinObject.getContributors()
            .stream()
            .map(attempt(CristinContributor::toNvaContributor))
            .map(Try::orElseThrow)
            .collect(Collectors.toList());
    }

    private List<URI> generateNvaHrcsCategoriesAndActivities() {
        if (isNull(extractCristinHrcsCategoriesAndActivities())) {
            return null;
        }
        List<URI> listOfCategoriesAndActivities = new ArrayList<>();
        listOfCategoriesAndActivities.addAll(extractHrcsCategories());
        listOfCategoriesAndActivities.addAll(extractHrcsActivities());
        return listOfCategoriesAndActivities;
    }

    private List<CristinHrcsCategoriesAndActivities> extractCristinHrcsCategoriesAndActivities() {
        return cristinObject.getHrcsCategoriesAndActivities();
    }

    private List<URI> extractHrcsCategories() {
        return extractCristinHrcsCategoriesAndActivities()
                .stream()
                .map(CristinHrcsCategoriesAndActivities::getCategory)
                .filter(CristinHrcsCategoriesAndActivities::validateCategory)
                .map(categoryId -> HRCS_CATEGORY_URI + HRCS_CATEGORIES_MAP.get(categoryId).toLowerCase(Locale.ROOT))
                .map(URI::create)
                .collect(Collectors.toList());
    }

    private Collection<URI> extractHrcsActivities() {
        return extractCristinHrcsCategoriesAndActivities()
                .stream()
                .map(CristinHrcsCategoriesAndActivities::getActivity)
                .filter(CristinHrcsCategoriesAndActivities::validateActivity)
                .map(activityId -> HRCS_ACTIVITY_URI + HRCS_ACTIVITIES_MAP.get(activityId).toLowerCase(Locale.ROOT))
                .map(URI::create)
                .collect(Collectors.toList());
    }

    private Reference buildReference() {
        PublicationInstanceBuilderImpl publicationInstanceBuilderImpl
            = new PublicationInstanceBuilderImpl(cristinObject);
        PublicationInstance<? extends Pages> publicationInstance
            = publicationInstanceBuilderImpl.build();
        PublicationContext publicationContext = attempt(this::buildPublicationContext).orElseThrow();
        return new Reference.Builder()
            .withPublicationInstance(publicationInstance)
            .withPublishingContext(publicationContext)
            .withDoi(extractDoi())
            .build();
    }

    private PublicationContext buildPublicationContext()
        throws InvalidIsbnException, InvalidIssnException, InvalidUnconfirmedSeriesException {
        if (isBook(cristinObject)) {
            return new NvaBookBuilder(cristinObject).buildBookForPublicationContext();
        }
        if (isJournal(cristinObject)) {
            return new PeriodicalBuilder(cristinObject).buildPeriodicalForPublicationContext();
        }
        if (isReport(cristinObject)) {
            return buildPublicationContextWhenMainCategoryIsReport();
        }
        if (isChapter(cristinObject)) {
            return buildChapterForPublicationContext();
        }
        if (isEvent(cristinObject)) {
            return buildEventForPublicationContext();
        }
        return null;
    }

    private PublicationContext buildPublicationContextWhenMainCategoryIsReport()
        throws InvalidIsbnException, InvalidIssnException, InvalidUnconfirmedSeriesException {
        if (isDegreePhd(cristinObject) || isDegreeMaster(cristinObject)) {
            return new NvaDegreeBuilder(cristinObject).buildDegree();
        }
        return new NvaReportBuilder(cristinObject).buildNvaReport();
    }

    private Chapter buildChapterForPublicationContext() {
        return new Chapter.Builder().build();
    }

    private PublicationContext buildEventForPublicationContext() {
        return new Event.Builder().build();
    }

    private PublicationDate extractPublicationDate() {
        return new PublicationDate.Builder().withYear(cristinObject.getPublicationYear().toString()).build();
    }

    private String extractMainTitle() {
        return extractCristinTitles()
            .filter(CristinTitle::isMainTitle)
            .findFirst()
            .map(CristinTitle::getTitle)
            .orElseThrow();
    }

    private Stream<CristinTitle> extractCristinTitles() {
        return Optional.ofNullable(cristinObject)
            .map(CristinObject::getCristinTitles)
            .stream()
            .flatMap(Collection::stream);
    }

    private URI extractLanguage() {
        return extractCristinTitles()
            .filter(CristinTitle::isMainTitle)
            .findFirst()
            .map(CristinTitle::getLanguagecode)
            .map(LanguageMapper::toUri)
            .orElse(LanguageMapper.LEXVO_URI_UNDEFINED);
    }

    private AdditionalIdentifier extractIdentifier() {
        return new AdditionalIdentifier(CristinObject.IDENTIFIER_ORIGIN, cristinObject.getId().toString());
    }

    private String extractNpiSubjectHeading() {
        CristinSubjectField subjectField = extractSubjectField();
        if (isNull(subjectField)) {
            return EMPTY_STRING;
        } else {
            return extractSubjectFieldCode(subjectField);
        }
    }

    private String extractSubjectFieldCode(CristinSubjectField subjectField) {
        return Optional.ofNullable(subjectField.getSubjectFieldCode())
            .map(String::valueOf)
            .orElseThrow(() -> new MissingFieldsException(CristinSubjectField.MISSING_SUBJECT_FIELD_CODE));
    }

    private boolean resourceTypeIsNotExpectedToHaveAnNpiSubjectHeading() {
        return !(isBook(cristinObject) || isReport(cristinObject));
    }

    private CristinSubjectField extractSubjectField() {
        if (resourceTypeIsNotExpectedToHaveAnNpiSubjectHeading()) {
            return null;
        }
        return extractCristinBookReport().getSubjectField();
    }

    private URI extractDoi() {
        if (isJournal(cristinObject)) {
            return Optional.of(extractCristinJournalPublication())
                .map(CristinJournalPublication::getDoi)
                .map(doiConverter::toUri)
                .orElse(null);
        }
        return null;
    }

    private List<CristinTags> extractCristinTags() {
        return Optional.ofNullable(cristinObject)
            .map(CristinObject::getTags)
            .orElse(null);
    }

    private String extractAbstract() {
        return extractCristinTitles()
            .filter(CristinTitle::isMainTitle)
            .findFirst()
            .map(CristinTitle::getAbstractText)
            .orElse(null);
    }

    private List<String> extractTags() {
        if (extractCristinTags() == null) {
            return null;
        }
        List<String> listOfTags = new ArrayList<>();
        for (CristinTags cristinTags : extractCristinTags()) {
            if (cristinTags.getBokmal() != null) {
                listOfTags.add(cristinTags.getBokmal());
            }
            if (cristinTags.getEnglish() != null) {
                listOfTags.add(cristinTags.getEnglish());
            }
            if (cristinTags.getNynorsk() != null) {
                listOfTags.add(cristinTags.getNynorsk());
            }
        }
        return listOfTags;
    }


}
