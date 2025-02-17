package no.unit.nva.publication.ticket.update;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import java.util.Objects;
import java.util.Optional;
import nva.commons.apigateway.exceptions.BadRequestException;
import nva.commons.core.JacocoGenerated;

@JsonTypeInfo(use = Id.NAME, property = "type")
public class UpdateViewStatusRequest {
    
    public static final String VIEW_STATUS = "viewStatus";
    @JsonProperty(VIEW_STATUS)
    private final ViewStatus viewStatus;
    
    @JsonCreator
    public UpdateViewStatusRequest(@JsonProperty(VIEW_STATUS) ViewStatus viewStatus) throws BadRequestException {
        this.viewStatus = parseStatus(viewStatus);
    }
    
    @Override
    @JacocoGenerated
    public int hashCode() {
        return Objects.hash(getViewStatus());
    }
    
    @Override
    @JacocoGenerated
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof UpdateViewStatusRequest)) {
            return false;
        }
        UpdateViewStatusRequest that = (UpdateViewStatusRequest) o;
        return getViewStatus() == that.getViewStatus();
    }
    
    public ViewStatus getViewStatus() {
        return viewStatus;
    }
    
    private ViewStatus parseStatus(ViewStatus viewStatus) throws BadRequestException {
        return Optional.ofNullable(viewStatus)
                   .orElseThrow(() -> new BadRequestException("viewStatus must be " + ViewStatus.legalValues()));
    }
}
