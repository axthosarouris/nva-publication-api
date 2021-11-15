package no.unit.nva.publication.s3imports;

import static no.unit.nva.publication.s3imports.S3ImportsConfig.s3ImportsMapper;
import static nva.commons.core.attempt.Try.attempt;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JavaType;
import java.net.URI;
import java.time.Instant;
import java.util.Map;
import nva.commons.core.JacocoGenerated;

/**
 * Event containing the contents of a file and additional information that are necessary for processing the file
 * content.
 *
 * <p>In its present form the {@link FileContentsEvent} contains also a field with the name "publicationsOwner" which
 * is  specific to the task of importing Cristin records.  In the future, this should be replaced by a more generic
 * format such as a {@link Map} annotated with "&#64;JsonAnySetter".
 *
 * @param <T> the class modeling the data structure of the file content.
 */
public class FileContentsEvent<T> {

    @JsonIgnore
    public static final String FILE_URI = "fileUri";
    @JsonIgnore
    public static final String TIMESTAMP = "timestamp";
    public static final String TOPIC = "topic";
    public static final String SUBTOPIC = "subtopic";
    @JsonIgnore
    protected static final String CONTENTS_FIELD = "contents";
    @JsonProperty(FILE_URI)
    private final URI fileUri;
    @JsonProperty(TIMESTAMP)
    private final Instant timestamp;
    @JsonProperty(CONTENTS_FIELD)
    private final T contents;
    @JsonProperty(TOPIC)
    private final String topic;
    @JsonProperty(SUBTOPIC)
    private final String subtopic;

    @JacocoGenerated
    @JsonCreator
    public FileContentsEvent(
        @JsonProperty(TOPIC) String topic,
        @JsonProperty(SUBTOPIC) String subtopic,
        @JsonProperty(FILE_URI) URI fileUri,
        @JsonProperty(TIMESTAMP) Instant timestamp,
        @JsonProperty(CONTENTS_FIELD) T contents) {
        this.topic = topic;
        this.subtopic = subtopic;
        this.fileUri = fileUri;
        this.timestamp = timestamp;
        this.contents = contents;
    }

    public static <T> FileContentsEvent<T> fromJson(String jsonString, Class<T> contentsClass) {
        JavaType javaType = constructJavaType(contentsClass);
        return attempt(() -> s3ImportsMapper
            .<FileContentsEvent<T>>readValue(jsonString, javaType)).orElseThrow();
    }

    @JacocoGenerated
    public String getTopic() {
        return topic;
    }

    @JacocoGenerated
    public String getSubtopic() {
        return subtopic;
    }

    @JacocoGenerated
    public URI getFileUri() {
        return fileUri;
    }

    @JacocoGenerated
    public Instant getTimestamp() {
        return timestamp;
    }

    @JacocoGenerated
    public T getContents() {
        return contents;
    }

    private static <T> JavaType constructJavaType(Class<T> contentsClass) {
        return s3ImportsMapper.getTypeFactory().constructParametricType(FileContentsEvent.class, contentsClass);
    }
}
