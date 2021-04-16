package no.unit.nva.dataimport;

import static java.util.Objects.nonNull;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.net.URI;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import nva.commons.core.JacocoGenerated;
import nva.commons.core.JsonSerializable;

public class ImportRequest implements JsonSerializable {

    public static final String PATH_DELIMITER = "/";
    public static final String S3_LOCATION_FIELD = "s3Location";
    public static final String TABLE_FIELD = "table";
    public static final String MISSING_FIELD_MESSAGE_PATTERN = "\"%s\"  field is missing";
    @JsonProperty("s3Location")
    private URI s3Location;
    @JsonProperty("table")
    private String table;

    //Default serializer necessary for AWS's serializer.
    @JacocoGenerated
    public ImportRequest() {

    }

    public ImportRequest(String s3location,
                         String table) {

        this.table = table;
        this.s3Location = Optional.ofNullable(s3location).map(URI::create).orElse(null);
    }

    /**
     * Workaround for dealing with the problem of AWS serializing objects.
     *
     * @param request a Map of String keys and String values containing the input parameters.
     * @return the input as an {@link ImportRequest}
     */
    public static ImportRequest fromMap(Map<String, String> request) {
        String s3Location = extractFieldFromMap(request, S3_LOCATION_FIELD);
        String table = extractFieldFromMap(request, TABLE_FIELD);
        return new ImportRequest(s3Location, table);
    }

    /**
     * Workaround for dealing with the problem of AWS serializing objects.
     *
     * @return the object as a {@link Map}
     */
    public Map<String, String> toMap() {
        Map<String, String> map = new ConcurrentHashMap<>();
        if (nonNull(getS3Location())) {
            map.put(S3_LOCATION_FIELD, getS3Location());
        }
        if (nonNull(getTable())) {
            map.put(TABLE_FIELD, getTable());
        }
        return map;
    }

    @JacocoGenerated
    public String getS3Location() {
        return Optional.ofNullable(s3Location).map(URI::toString).orElse(null);
    }

    @JacocoGenerated
    public void setS3Location(String s3Location) {
        this.s3Location = URI.create(s3Location);
    }

    @JacocoGenerated
    public String getTable() {
        return table;
    }

    @JacocoGenerated
    public void setTable(String table) {
        this.table = table;
    }

    public String extractBucketFromS3Location() {
        return s3Location.getHost();
    }

    public String extractPathFromS3Location() {
        return Optional.ofNullable(s3Location)
                   .map(URI::getPath)
                   .map(this::removeRoot)
                   .orElse(null);
    }

    @JacocoGenerated
    @Override
    public int hashCode() {
        return Objects.hash(getS3Location(), getTable());
    }

    @JacocoGenerated
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ImportRequest)) {
            return false;
        }
        ImportRequest request = (ImportRequest) o;
        return Objects.equals(getS3Location(), request.getS3Location())
               && Objects.equals(getTable(), request.getTable());
    }

    @Override
    @JacocoGenerated
    public String toString() {
        return toJsonString();
    }

    private static String extractFieldFromMap(Map<String, String> request, String fieldName) {
        return request.keySet().stream()
                   .filter(fieldName::equalsIgnoreCase)
                   .findFirst()
                   .map(request::get)
                   .orElseThrow(() -> errorForMissingField(fieldName));
    }

    private static IllegalArgumentException errorForMissingField(String fieldName) {
        return new IllegalArgumentException(String.format(MISSING_FIELD_MESSAGE_PATTERN, fieldName));
    }

    private String removeRoot(String path) {
        return path.startsWith(PATH_DELIMITER) ? path.substring(1) : path;
    }
}
