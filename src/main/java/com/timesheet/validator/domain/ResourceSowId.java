package com.timesheet.validator.domain;

import lombok.*;
import java.io.Serializable;

@Data @NoArgsConstructor @AllArgsConstructor
public class ResourceSowId implements Serializable {
    private String resourceId;
    private String sowNumber;
}
