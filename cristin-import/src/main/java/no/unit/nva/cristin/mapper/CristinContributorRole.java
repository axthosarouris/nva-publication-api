package no.unit.nva.cristin.mapper;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import no.unit.nva.model.Role;

@Data
@Builder(
    builderClassName = "CristinObjectBuilder",
    toBuilder = true,
    builderMethodName = "builder",
    buildMethodName = "build",
    setterPrefix = "with"
)
@AllArgsConstructor(access = AccessLevel.PACKAGE)
public class CristinContributorRole {

    @JsonProperty("rollekode")
    private CristinContributorRoleCode roleCode;

    public CristinContributorRole() {

    }

    public Role toNvaRole() {
        if (CristinContributorRoleCode.CREATOR.equals(roleCode)) {
            return Role.CREATOR;
        } else if (CristinContributorRoleCode.EDITOR.equals(roleCode)) {
            return Role.EDITOR;
        } else {
            return Role.OTHER;
        }
    }
}
