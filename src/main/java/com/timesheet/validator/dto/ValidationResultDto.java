package com.timesheet.validator.dto;

import lombok.*;
import java.util.List;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class ValidationResultDto {
    private String sessionId;
    private boolean passed;
    private int errorCount;
    private int warningCount;
    private List<IssueDto> errors;
    private List<IssueDto> warnings;

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class IssueDto {
        private String ruleId;
        private String severity;
        private String sheetName;
        private Integer rowIdx;
        private Integer colIdx;
        private String fieldName;
        private String message;
    }
}
