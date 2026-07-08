package com.timesheet.validator.domain;

import lombok.*;
import javax.persistence.*;

@Entity
@Table(name = "RESOURCE_SOW")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
@IdClass(ResourceSowId.class)
public class ResourceSow {

    @Id @Column(name = "RESOURCE_ID")
    private String resourceId;

    @Id @Column(name = "SOW_NUMBER")
    private String sowNumber;

    @Column(name = "ROLE_IN_SOW")
    private String roleInSow;
}
