package com.timesheet.validator.domain;
import lombok.*; import javax.persistence.*; import java.time.LocalDateTime; import java.util.*;
@Entity @Table(name="APP_USER") @Data @Builder @NoArgsConstructor @AllArgsConstructor
public class AppUser {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="USERNAME",unique=true,nullable=false) private String username;
    @Column(name="PASSWORD",nullable=false) private String password;
    @Column(name="FULL_NAME") private String fullName;
    @Column(name="EMAIL") private String email;
    @Column(name="ENABLED") private Boolean enabled;
    @Column(name="RESOURCE_ID") private String resourceId;
    @Column(name="CREATED_AT") private LocalDateTime createdAt;

    @ManyToMany(fetch=FetchType.EAGER)
    @JoinTable(name="USER_ROLE",
        joinColumns=@JoinColumn(name="USER_ID"),
        inverseJoinColumns=@JoinColumn(name="ROLE_ID"))
    @Builder.Default private Set<Role> roles = new HashSet<>();

    @PrePersist public void pre() { if(createdAt==null) createdAt=LocalDateTime.now(); }
}
