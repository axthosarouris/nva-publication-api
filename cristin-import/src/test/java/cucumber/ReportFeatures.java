package cucumber;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsEqual.equalTo;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import no.unit.nva.model.contexttypes.PublicationContext;
import no.unit.nva.model.contexttypes.PublishingHouse;
import no.unit.nva.model.contexttypes.Report;
import no.unit.nva.model.contexttypes.UnconfirmedPublisher;

public class ReportFeatures {
    
    private final ScenarioContext scenarioContext;
    
    public ReportFeatures(ScenarioContext scenarioContext) {
        this.scenarioContext = scenarioContext;
    }
    
    @Then("the NVA Resource Report has a PublicationContext with a publisher with name equal to {string}")
    public void theNvaResourceReportHasAPublicationContextWithPublisherWithNameEqualTo(String expectedPublisherName) {
        PublicationContext context = scenarioContext.getNvaEntry()
                                         .getEntityDescription()
                                         .getReference()
                                         .getPublicationContext();
        Report reportContext = (Report) context;
        PublishingHouse actualPublisher = reportContext.getPublisher();
        PublishingHouse expectedPublisher = new UnconfirmedPublisher(expectedPublisherName);
        assertThat(actualPublisher, is(equalTo(expectedPublisher)));
    }
    
    @Given("the Cristin Result does not mention a publisher in the alternative field")
    public void thatTheCristinResultHasAnEmptyPublisherNameField() {
        scenarioContext.getCristinEntry().getBookOrReportMetadata().setPublisherName(null);
    }
    
    @Then("the NVA Resource Report has a Publisher that cannot be verified through a URI")
    public void theNvaResourceReportHasAPublisherThatCannotBeVerifiedThroughAUri() {
        PublicationContext context = scenarioContext.getNvaEntry()
                                         .getEntityDescription()
                                         .getReference()
                                         .getPublicationContext();
        Report reportContext = (Report) context;
        PublishingHouse publisher = reportContext.getPublisher();
        assertThat(publisher, is(instanceOf(UnconfirmedPublisher.class)));
    }
}
