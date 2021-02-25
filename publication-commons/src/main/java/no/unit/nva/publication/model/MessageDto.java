package no.unit.nva.publication.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.net.URI;
import java.time.Instant;
import java.util.Objects;
import no.unit.nva.identifiers.SortableIdentifier;
import no.unit.nva.publication.storage.model.Message;
import nva.commons.core.JacocoGenerated;

public class MessageDto {

    @JsonProperty("id")
    URI messageId;
    @JsonProperty("identifier")
    SortableIdentifier messageIdentifier;
    @JsonProperty("sender")
    private String senderIdentifier;
    @JsonProperty("owner")
    private String ownerIdentifier;
    @JsonProperty("text")
    private String text;
    @JsonProperty("date")
    private Instant date;
    @JsonProperty("isDoiRequestRelated")
    private boolean doiRequestRelated;

    public static MessageDto fromMessage(Message message) {
        MessageDto messageDto = new MessageDto();
        messageDto.setOwnerIdentifier(message.getOwner());
        messageDto.setSenderIdentifier(message.getSender());
        messageDto.setText(message.getText());
        messageDto.setDate(message.getCreatedTime());
        messageDto.setMessageId(message.getId());
        messageDto.setMessageIdentifier(message.getIdentifier());
        messageDto.setDoiRequestRelated(message.isDoiRequestRelated());
        return messageDto;
    }

    @JacocoGenerated
    @Override
    public int hashCode() {
        return Objects.hash(getMessageId(),
            getMessageIdentifier(),
            getSenderIdentifier(),
            getOwnerIdentifier(),
            getText(),
            getDate(), isDoiRequestRelated());
    }

    @JacocoGenerated
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof MessageDto)) {
            return false;
        }
        MessageDto that = (MessageDto) o;
        return isDoiRequestRelated() == that.isDoiRequestRelated()
               && Objects.equals(getMessageId(), that.getMessageId())
               && Objects.equals(getMessageIdentifier(), that.getMessageIdentifier())
               && Objects.equals(getSenderIdentifier(), that.getSenderIdentifier())
               && Objects.equals(getOwnerIdentifier(), that.getOwnerIdentifier())
               && Objects.equals(getText(), that.getText())
               && Objects.equals(getDate(), that.getDate());
    }

    @JacocoGenerated
    public boolean isDoiRequestRelated() {
        return doiRequestRelated;
    }

    @JacocoGenerated
    public void setDoiRequestRelated(boolean doiRequestRelated) {
        this.doiRequestRelated = doiRequestRelated;
    }

    @JacocoGenerated
    public URI getMessageId() {
        return messageId;
    }

    @JacocoGenerated
    public void setMessageId(URI messageId) {
        this.messageId = messageId;
    }

    @JacocoGenerated
    public SortableIdentifier getMessageIdentifier() {
        return messageIdentifier;
    }

    @JacocoGenerated
    public void setMessageIdentifier(SortableIdentifier messageIdentifier) {
        this.messageIdentifier = messageIdentifier;
    }

    @JacocoGenerated
    public Instant getDate() {
        return date;
    }

    @JacocoGenerated
    public void setDate(Instant date) {
        this.date = date;
    }

    @JacocoGenerated
    public String getText() {
        return text;
    }

    @JacocoGenerated
    public void setText(String text) {
        this.text = text;
    }

    @JacocoGenerated
    public String getSenderIdentifier() {
        return senderIdentifier;
    }

    @JacocoGenerated
    public void setSenderIdentifier(String senderIdentifier) {
        this.senderIdentifier = senderIdentifier;
    }

    @JacocoGenerated
    public String getOwnerIdentifier() {
        return ownerIdentifier;
    }

    @JacocoGenerated
    public void setOwnerIdentifier(String ownerIdentifier) {
        this.ownerIdentifier = ownerIdentifier;
    }
}
