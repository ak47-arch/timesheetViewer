package com.timesheet.validator.domain;
import lombok.*; import javax.persistence.*;
@Entity @Table(name="ROLE") @Data @Builder @NoArgsConstructor @AllArgsConstructor
public class Role {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="NAME",unique=true,nullable=false) private String name;
    @Column(name="DESCRIPTION") private String description;
}
