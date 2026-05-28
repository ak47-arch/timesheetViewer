package com.timesheet.validator.domain;
import lombok.*; import javax.persistence.*; import java.time.LocalDateTime;
@Entity @Table(name="UPLOAD_SESSION") @Data @Builder @NoArgsConstructor @AllArgsConstructor
public class UploadSession {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="SESSION_ID",unique=true,nullable=false) private String sessionId;
    @Column(name="FILE_NAME") private String fileName;
    @Column(name="UPLOADED_AT") private LocalDateTime uploadedAt;
    @Column(name="SHEET_COUNT") private Integer sheetCount;
    @Column(name="STATUS") private String status;
    @PrePersist public void pre() { if(uploadedAt==null) uploadedAt=LocalDateTime.now(); }
}
