package no.unit.nva.cristin.lambda.dtos;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.net.URI;
import no.unit.nva.cristin.mapper.CristinObject;
import no.unit.nva.publication.s3imports.FileContentsEvent;

/**
 * Wrapper class for using in Lambda functions.
 */

public class CristinObjectEvent extends FileContentsEvent<CristinObject> {

    @JsonCreator
    public CristinObjectEvent(@JsonProperty(FILE_URI) URI fileUri,
                              @JsonProperty(CONTENTS_FIELD) CristinObject contents,
                              @JsonProperty(PUBLICATIONS_OWNER_FIELD) String publicationsOwner) {

        super(fileUri, contents, publicationsOwner);
    }
}
