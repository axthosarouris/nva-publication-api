package no.unit.nva.cristin.lambda.constants;

import java.net.URI;
import java.util.UUID;
import nva.commons.core.Environment;
import nva.commons.core.JacocoGenerated;

public final class MappingConstants {

    public static final Environment ENVIRONMENT = new Environment();
    public static final boolean SHOULD_CREATE_CONTRIBUTOR_ID = createCristinContributorId();
    public static final URI CRISTIN_PERSONS_URI = URI.create("https://api.cristin.no/person/");
    public static final URI CRISTIN_ORG_URI = readCristinOrgUriFromEnvOrDefault();
    //NIL UUID is ignored by the Json serializer and cannot be used
    public static final UUID DUMMY_UUID = new UUID(0, 1);
    public static final String PUBLIC_DOMAIN_LICENSE = "CC0";

    private MappingConstants() {

    }

    private static URI readCristinOrgUriFromEnvOrDefault() {
        String defaultUriForReferencingCristinOrgs = "https://api.cristin.no/v2/units/";
        return ENVIRONMENT.readEnvOpt("CRISTIN_ORG_URI")
                   .map(URI::create)
                   .orElse(URI.create(defaultUriForReferencingCristinOrgs));
    }

    @JacocoGenerated
    private static boolean createCristinContributorId() {
        return ENVIRONMENT.readEnvOpt("CREATE_CONTRIBUTOR_ID")
                   .map(Boolean::parseBoolean)
                   .orElse(false);
    }
}
