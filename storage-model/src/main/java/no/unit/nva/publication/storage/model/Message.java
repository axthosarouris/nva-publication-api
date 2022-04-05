package no.unit.nva.publication.storage.model;

import static java.util.Objects.nonNull;
import static no.unit.nva.publication.storage.model.DataEntry.nextRowVersion;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import no.unit.nva.commons.json.JsonSerializable;
import no.unit.nva.identifiers.SortableIdentifier;
import no.unit.nva.model.EntityDescription;
import no.unit.nva.model.Publication;
import no.unit.nva.publication.storage.model.daos.Dao;
import no.unit.nva.publication.storage.model.daos.MessageDao;
import nva.commons.core.JacocoGenerated;

@JsonTypeInfo(use = Id.NAME, property = "type")
public class Message implements WithIdentifier,
                                WithStatus,
                                RowLevelSecurity,
                                DataEntry,
                                ConnectedToResource,
                                JsonSerializable {

    public static final MessageType DEFAULT_MESSAGE_TYPE = MessageType.SUPPORT;
    public static final String TYPE = "Message";
    private SortableIdentifier identifier;
    private String owner;
    private URI customerId;
    private MessageStatus status;
    private String sender;
    private SortableIdentifier resourceIdentifier;
    private String text;
    private Instant createdTime;
    private String resourceTitle;
    private MessageType messageType;
    private String rowVersion;

    @JacocoGenerated
    public Message() {

    }

    public static MessageBuilder builder() {
        return new MessageBuilder();
    }

    public static Message doiRequestMessage(UserInstance sender,
                                            Publication publication,
                                            String messageText,
                                            SortableIdentifier messageIdentifier,
                                            Clock clock) {
        return buildMessage(sender, publication, messageText, messageIdentifier, clock)
            .withMessageType(MessageType.DOI_REQUEST)
            .build();
    }

    public static Message supportMessage(UserInstance sender,
                                         Publication publication,
                                         String messageText,
                                         SortableIdentifier messageIdentifier,
                                         Clock clock) {
        return buildMessage(sender, publication, messageText, messageIdentifier, clock)
            .withMessageType(MessageType.SUPPORT)
            .build();
    }

    @Deprecated
    public static Message supportMessage(UserInstance sender,
                                         Publication publication,
                                         String messageText,
                                         Clock clock) {
        return supportMessage(sender, publication, messageText, null, clock);
    }

    public MessageType getMessageType() {
        return nonNull(messageType) ? messageType : DEFAULT_MESSAGE_TYPE;
    }

    public void setMessageType(MessageType messageType) {
        this.messageType = messageType;
    }

    @Override
    public SortableIdentifier getIdentifier() {
        return identifier;
    }

    @Override
    public void setIdentifier(SortableIdentifier identifier) {
        this.identifier = identifier;
    }

    @Override
    public URI getCustomerId() {
        return customerId;
    }

    @Override
    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public void setCustomerId(URI customerId) {
        this.customerId = customerId;
    }

    public MessageStatus getStatus() {
        return status;
    }

    public void setStatus(MessageStatus status) {
        this.status = status;
    }

    public String getSender() {
        return sender;
    }

    public void setSender(String sender) {
        this.sender = sender;
    }

    /**
     * Deprecated method. Preserved for backwards compatibility.
     *
     * @return true if message is doi request related.
     */
    //Use getMessageType
    @Deprecated
    public boolean isDoiRequestRelated() {
        return MessageType.DOI_REQUEST.equals(getMessageType());
    }

    /**
     * Deprecated method. Preserved for backwards compatibility
     *
     * @param doiRequestRelated set true if Message is related to the respective DOI request.
     */
    //Use setMessageType
    @Deprecated
    public void setDoiRequestRelated(boolean doiRequestRelated) {
        if (doiRequestRelated) {
            setMessageType(MessageType.DOI_REQUEST);
        } else {
            setMessageType(MessageType.SUPPORT);
        }
    }

    @Override
    public SortableIdentifier getResourceIdentifier() {
        return resourceIdentifier;
    }

    public void setResourceIdentifier(SortableIdentifier resourceIdentifier) {
        this.resourceIdentifier = resourceIdentifier;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public Instant getCreatedTime() {
        return createdTime;
    }

    public void setCreatedTime(Instant createdTime) {
        this.createdTime = createdTime;
    }

    public String getResourceTitle() {
        return resourceTitle;
    }

    public void setResourceTitle(String resourceTitle) {
        this.resourceTitle = resourceTitle;
    }

    @Override
    public String getStatusString() {
        return status.toString();
    }

    @Override
    public Publication toPublication() {
        throw new UnsupportedOperationException();
    }

    @JacocoGenerated
    @Override
    public String getRowVersion() {
        return this.rowVersion;
    }

    @Override
    @JacocoGenerated
    public void setRowVersion(String rowVersion) {
        this.rowVersion = rowVersion;
    }

    @Override
    public Dao<?> toDao() {
        return new MessageDao(this);
    }

    @Override
    @JacocoGenerated
    public int hashCode() {
        return Objects.hash(getIdentifier(), getOwner(), getCustomerId(), getStatus(), getSender(),
                            isDoiRequestRelated(),
                            getResourceIdentifier(), getText(), getCreatedTime(), getResourceTitle(), getMessageType());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Message)) {
            return false;
        }
        Message message = (Message) o;
        return isDoiRequestRelated() == message.isDoiRequestRelated()
               && Objects.equals(getIdentifier(), message.getIdentifier())
               && Objects.equals(getOwner(), message.getOwner())
               && Objects.equals(getCustomerId(), message.getCustomerId())
               && getStatus() == message.getStatus()
               && Objects.equals(getSender(), message.getSender())
               && Objects.equals(getResourceIdentifier(), message.getResourceIdentifier())
               && Objects.equals(getText(), message.getText())
               && Objects.equals(getCreatedTime(), message.getCreatedTime())
               && Objects.equals(getResourceTitle(), message.getResourceTitle())
               && getMessageType() == message.getMessageType();
    }

    @Override
    @JacocoGenerated
    public String toString() {
        return toJsonString();
    }

    public MessageBuilder copy() {
        return Message.builder()
            .withCreatedTime(this.getCreatedTime())
            .withCustomerId(this.getCustomerId())
            .withIdentifier(this.getIdentifier())
            .withMessageType(this.getMessageType())
            .withResourceIdentifier(getResourceIdentifier())
            .withStatus(this.getStatus())
            .withOwner(this.getOwner())
            .withSender(this.getSender())
            .withText(this.getText())
            .withResourceTitle(this.getResourceTitle())
            .withRowVersion(this.getRowVersion());
    }

    private static MessageBuilder buildMessage(UserInstance sender, Publication publication,
                                               String messageText, SortableIdentifier messageIdentifier,
                                               Clock clock) {
        return Message.builder()
            .withStatus(MessageStatus.UNREAD)
            .withResourceIdentifier(publication.getIdentifier())
            .withCustomerId(sender.getOrganizationUri())
            .withText(messageText)
            .withSender(sender.getUserIdentifier())
            .withOwner(publication.getOwner())
            .withResourceTitle(extractTitle(publication))
            .withCreatedTime(clock.instant())
            .withIdentifier(messageIdentifier)
            .withRowVersion(nextRowVersion());
    }

    private static String extractTitle(Publication publication) {
        return Optional.of(publication)
            .map(Publication::getEntityDescription)
            .map(EntityDescription::getMainTitle)
            .orElse(null);
    }
}
