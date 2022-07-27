package no.unit.nva.publication.model.business;

import static no.unit.nva.hamcrest.DoesNotHaveEmptyValues.doesNotHaveEmptyValues;
import static no.unit.nva.publication.model.business.Entity.nextVersion;
import static no.unit.nva.testutils.RandomDataGenerator.randomString;
import static no.unit.nva.testutils.RandomDataGenerator.randomUri;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.hamcrest.core.IsInstanceOf.instanceOf;
import static org.hamcrest.core.IsNot.not;
import java.time.Clock;
import java.time.Instant;
import java.util.stream.Stream;
import no.unit.nva.identifiers.SortableIdentifier;
import no.unit.nva.model.Publication;
import no.unit.nva.model.testing.PublicationGenerator;
import no.unit.nva.publication.model.storage.Dao;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class DataEntryTest {
    
    static Stream<Tuple> resourceProvider() {
        var publication = PublicationGenerator.randomPublication();
        final var leftResource = Resource.fromPublication(publication);
        final var rightResource = leftResource.copy().withRowVersion(nextVersion()).build();
        final var leftDoiRequest = DoiRequest.newDoiRequestForResource(leftResource, Instant.now());
        final var rightDoiRequest = leftDoiRequest.copy().withRowVersion(nextVersion()).build();
        final var leftMessage = randomMessage(publication);
        final var rightMessage = leftMessage.copy().withRowVersion(nextVersion()).build();
        return Stream.of(new Tuple(leftResource, rightResource),
            new Tuple(leftDoiRequest, rightDoiRequest),
            new Tuple(leftMessage, rightMessage));
    }
    
    //This test guarantees backwards compatibility and the requirements can change when optimistic concurrency control
    // is implemented.
    @ParameterizedTest(name = "should return equals true when two resources differ only in their row version")
    @MethodSource("resourceProvider")
    void shouldReturnEqualsTrueWhenTwoResourcesDifferOnlyInTheirRowVersion(Tuple tuple) {
        assertThat(tuple.left, doesNotHaveEmptyValues());
        assertThat(tuple.right.getVersion(), is(not(equalTo(tuple.left.getVersion()))));
        assertThat(tuple.right, is(equalTo(tuple.left)));
    }
    
    //This test guarantees backwards compatibility and the requirements can change when optimistic concurrency control
    // is implemented.
    @ParameterizedTest(name = "should return the same hash code when two resources differ only in their row version")
    @MethodSource("resourceProvider")
    void shouldReturnTheSameHashCodeWhenTwoResourcesDifferOnlyInTheirRowVersion(Tuple tuple) {
        assertThat(tuple.left, doesNotHaveEmptyValues());
        assertThat(tuple.left.getVersion(), is(not(equalTo(tuple.right.getVersion()))));
        assertThat(tuple.left.hashCode(), is(equalTo(tuple.right.hashCode())));
    }
    
    @Test
    void shouldCreateNewRowVersionWhenRefreshed() {
        var publication = PublicationGenerator.randomPublication();
        var resource = Resource.fromPublication(publication);
        var oldRowVersion = resource.getVersion();
        var newRowVersion = resource.refreshVersion().getVersion();
        assertThat(newRowVersion, is(not(equalTo(oldRowVersion))));
    }
    
    @ParameterizedTest(name = "should return Dao object:{0}")
    @MethodSource("resourceProvider")
    void shouldReturnDaoObject(Tuple resourceUpdate) {
        assertThat(resourceUpdate.left.toDao(), is(instanceOf(Dao.class)));
    }
    
    private static Message randomMessage(Publication publication) {
        var user = UserInstance.create(randomString(), randomUri());
        var clock = Clock.systemDefaultZone();
        return Message.create(user, publication, randomString(), SortableIdentifier.next(), clock, MessageType.SUPPORT);
    }
    
    private static class Tuple {
        
        private final Entity left;
        private final Entity right;
        
        public Tuple(Entity left, Entity right) {
            
            this.left = left;
            this.right = right;
        }
    }
}
