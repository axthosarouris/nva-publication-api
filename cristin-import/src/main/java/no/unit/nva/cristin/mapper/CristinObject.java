package no.unit.nva.cristin.mapper;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import no.unit.nva.model.Publication;
import nva.commons.core.JsonSerializable;

@Data
@Builder(
    builderClassName = "CristinObjectBuilder",
    toBuilder = true,
    builderMethodName = "builder",
    buildMethodName = "build",
    setterPrefix = "with"
)
@AllArgsConstructor(access = AccessLevel.PACKAGE)
public class CristinObject implements JsonSerializable {

    public static final String PUBLICATION_OWNER_FIELD = "publicationOwner";
    public static String IDENTIFIER_ORIGIN = "Cristin";
    @JsonProperty("id")
    private Integer id;
    @JsonProperty("arstall")
    private String publicationYear;
    @JsonProperty("dato_opprettet")
    private LocalDate entryCreationDate;
    @JsonProperty("VARBEID_SPRAK")
    private List<CristinTitle> cristinTitles;
    @JsonProperty("varbeidhovedkatkode")
    private CristinMainCategory mainCategory;
    @JsonProperty("varbeidunderkatkode")
    private CristinSecondaryCategory secondaryCategory;
    @JsonProperty("VARBEID_PERSON")
    private List<CristinContributor> contributors;

    @JsonProperty
    private String publicationOwner;

    public CristinObject() {

    }

    public Publication toPublication() {
        return new CristinMapper(this).generatePublication();
    }

    public CristinObjectBuilder copy() {
        return this.toBuilder();
    }
}
