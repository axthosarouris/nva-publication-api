package no.unit.nva.publication.events.handlers.create;

import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.mockito.Mockito.mock;
import com.amazonaws.services.lambda.runtime.Context;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import no.unit.nva.api.PublicationResponse;
import no.unit.nva.commons.json.JsonUtils;
import no.unit.nva.events.models.EventReference;
import no.unit.nva.model.testing.PublicationGenerator;
import no.unit.nva.publication.create.CreatePublicationRequest;
import no.unit.nva.s3.S3Driver;
import no.unit.nva.stubs.FakeS3Client;
import no.unit.nva.testutils.EventBridgeEventBuilder;
import nva.commons.core.paths.UnixPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CreatePublishedPublicationHandlerTest {

    public static final Context CONTEXT = mock(Context.class);
    public static final String DATA_IMPORT_TOPIC = "PublicationService.DataImport.DataEntry";
    public static final String SCOPUS_IMPORT_SUBTOPIC = "PublicationService.ScopusData.DataEntry";
    CreatePublishedPublicationHandler handler;
    private ByteArrayOutputStream outputStream;
    private FakeS3Client fakeS3Client;
    private S3Driver s3Driver;

    @BeforeEach
    public void init() {
        this.outputStream = new ByteArrayOutputStream();
        this.fakeS3Client = new FakeS3Client();
        this.s3Driver = new S3Driver(fakeS3Client, "notimportant");
        this.handler = new CreatePublishedPublicationHandler(fakeS3Client);
    }

    @Test
    void shouldReceiveAnEventReferenceAndReadFileFromS3() throws IOException {
        var samplePublication = sampleCreatePublicationRequest();
        var s3FileUri = storeRequestInS3(samplePublication);
        var response = sendMessageToEventHandler(s3FileUri);

        String actualSampleValue = response.getEntityDescription().getMainTitle();
        String expectedSampleValue = samplePublication.getEntityDescription().getMainTitle();
        assertThat(actualSampleValue, is(equalTo(expectedSampleValue)));
    }

    private PublicationResponse sendMessageToEventHandler(URI s3FileUri) throws JsonProcessingException {
        var eventBody = new EventReference(DATA_IMPORT_TOPIC, SCOPUS_IMPORT_SUBTOPIC, s3FileUri);
        var event = EventBridgeEventBuilder.sampleEvent(eventBody);
        handler.handleRequest(event, outputStream, CONTEXT);
        return parseResponse(outputStream.toString());
    }

    private CreatePublicationRequest sampleCreatePublicationRequest() {
        return CreatePublicationRequest.fromPublication(PublicationGenerator.randomPublication());
    }

    private URI storeRequestInS3(CreatePublicationRequest request) throws IOException {
        var json = JsonUtils.dtoObjectMapper.writeValueAsString(request);
        return s3Driver.insertFile(UnixPath.of(randomString()), json);
    }

    private PublicationResponse parseResponse(String responseString)
        throws com.fasterxml.jackson.core.JsonProcessingException {
        return JsonUtils.dtoObjectMapper.readValue(responseString, PublicationResponse.class);
    }
}
