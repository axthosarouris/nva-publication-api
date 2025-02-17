package no.unit.nva.doi;

import static no.unit.nva.doi.LandingPageUtil.LANDING_PAGE_UTIL;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import no.unit.nva.identifiers.SortableIdentifier;
import no.unit.nva.model.Contributor;
import no.unit.nva.model.EntityDescription;
import no.unit.nva.model.Organization;
import no.unit.nva.model.Publication;
import no.unit.nva.model.PublicationDate;
import no.unit.nva.model.Reference;
import no.unit.nva.model.instancetypes.PublicationInstance;
import no.unit.nva.transformer.dto.CreatorDto;
import no.unit.nva.transformer.dto.DataCiteMetadataDto;
import no.unit.nva.transformer.dto.IdentifierDto;
import no.unit.nva.transformer.dto.PublisherDto;
import no.unit.nva.transformer.dto.ResourceTypeDto;
import no.unit.nva.transformer.dto.TitleDto;

/**
 * DataCiteMetadataDtoMapper is mapping event sources (ie {@link Publication}) into a {@link DataCiteMetadataDto}.
 *
 * <p>{@link DataCiteMetadataDto} is the root object generated by JAXB from DataCite XML Schema for Metadata,
 * which is the required input for {@link no.unit.nva.transformer.Transformer}.
 */
public final class DataCiteMetadataDtoMapper {
    
    private DataCiteMetadataDtoMapper() {
    }
    
    /**
     * Maps a Publication to DataCiteMetadataDto. For use in the nva doi partner data Transformer.
     *
     * @param publication publication
     * @return DataCiteMetadataDto
     */
    public static DataCiteMetadataDto fromPublication(Publication publication) {
        return new DataCiteMetadataDto.Builder()
                   .withCreator(toCreatorDtoList(extractContributors(publication)))
                   .withIdentifier(toIdentifierDto(publication))
                   .withPublicationYear(extractPublicationYear(publication))
                   .withPublisher(extractPublisher(publication))
                   .withTitle(extractTitle(publication))
                   .withResourceType(toResourceTypeDto(publication))
                   .build();
    }
    
    private static List<Contributor> extractContributors(Publication publication) {
        return getEntityDescription(publication)
                   .map(EntityDescription::getContributors)
                   .orElse(null);
    }
    
    private static String extractPublicationYear(Publication publication) {
        return getEntityDescription(publication)
                   .map(EntityDescription::getDate)
                   .map(PublicationDate::getYear)
                   .orElse(null);
    }
    
    private static ResourceTypeDto toResourceTypeDto(Publication publication) {
        return getEntityDescription(publication)
                   .map(EntityDescription::getReference)
                   .map(Reference::getPublicationInstance)
                   .map(PublicationInstance::getInstanceType)
                   .map(DataCiteMetadataDtoMapper::newResourceTypeDto)
                   .orElse(null);
    }
    
    private static ResourceTypeDto newResourceTypeDto(String resourceType) {
        return new ResourceTypeDto.Builder()
                   .withValue(resourceType)
                   .build();
    }
    
    private static TitleDto extractTitle(Publication publication) {
        return getEntityDescription(publication)
                   .map(EntityDescription::getMainTitle)
                   .map(title -> new TitleDto.Builder().withValue(title).build())
                   .orElse(null);
    }
    
    private static Optional<EntityDescription> getEntityDescription(Publication publication) {
        return Optional.of(publication)
                   .map(Publication::getEntityDescription);
    }
    
    private static PublisherDto extractPublisher(Publication publication) {
        return Optional.of(publication)
                   .map(Publication::getPublisher)
                   .map(Organization::getId)
                   .map(DataCiteMetadataDtoMapper::newPublisherDto)
                   .orElse(null);
    }
    
    private static PublisherDto newPublisherDto(URI uri) {
        return new PublisherDto.Builder().withValue(uri.toString()).build();
    }
    
    private static IdentifierDto toIdentifierDto(Publication publication) {
        return Optional.of(publication)
                   .map(Publication::getIdentifier)
                   .map(SortableIdentifier::toString)
                   .map(LANDING_PAGE_UTIL::constructResourceUri)
                   .map(URI::toString)
                   .map(uriString -> new IdentifierDto.Builder().withValue(uriString).build())
                   .orElse(null);
    }
    
    private static List<CreatorDto> toCreatorDtoList(List<Contributor> contributors) {
        if (contributors == null) {
            return null;
        }
        return contributors.stream()
                   .map(DataCiteMetadataDtoMapper::toCreatorDto)
                   .collect(Collectors.toList());
    }
    
    private static CreatorDto toCreatorDto(Contributor contributor) {
        return new CreatorDto.Builder()
                   .withCreatorName(contributor.getIdentity().getName())
                   .build();
    }
}
