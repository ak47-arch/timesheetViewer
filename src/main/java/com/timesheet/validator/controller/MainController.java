package com.timesheet.validator.controller;

import com.timesheet.validator.domain.UploadSession;
import com.timesheet.validator.domain.ValidationRule;
import com.timesheet.validator.config.RuleCatalog;
import com.timesheet.validator.dto.SheetDto;
import com.timesheet.validator.dto.ValidationResultDto;
import com.timesheet.validator.repository.*;
import com.timesheet.validator.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.fasterxml.jackson.databind.ObjectMapper;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Controller
@RequiredArgsConstructor
@Slf4j
public class MainController {

    private final ExcelParserService    parser;
    private final ValidationService     validator;
    private final SheetViewService      sheetView;
    private final UploadSessionRepository sessionRepo;
    private final ValidationIssueRepository issueRepo;
    private final PublicHolidayRepository   holidayRepo;
    private final ResourceRepository        resourceRepo;
    private final RuleCatalog               ruleCatalog;

    // ── Home ─────────────────────────────────────────────────────────────────
    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("sessions",      sessionRepo.findAllByOrderByUploadedAtDesc());
        model.addAttribute("holidayCount",  holidayRepo.count());
        model.addAttribute("resourceCount", resourceRepo.count());
        model.addAttribute("holidays",      holidayRepo.findAll());
        model.addAttribute("ruleGroups",    ruleCatalog.getGroups());
        model.addAttribute("totalRuleCount", ruleCatalog.getToggleableRuleCount());
        return "pages/home";
    }

    // ── Upload ────────────────────────────────────────────────────────────────
    @PostMapping("/upload")
    public String upload(@RequestParam("file") MultipartFile file,
                         @RequestParam(value = "rules", required = false)
                         List<String> selectedRules,
                         RedirectAttributes ra) {
        if (file.isEmpty()) {
            ra.addFlashAttribute("error", "Please select an Excel (.xlsx) file.");
            return "redirect:/";
        }
        if (selectedRules == null || selectedRules.isEmpty()) {
            ra.addFlashAttribute(
                    "error",
                    "Please select at least one validation rule."
            );
            return "redirect:/";
        }
        String name = file.getOriginalFilename();
        if (name == null || !name.toLowerCase().endsWith(".xlsx")) {
            ra.addFlashAttribute("error", "Only .xlsx files are supported.");
            return "redirect:/";
        }
        try {
            String sessionId = parser.parse(file, selectedRules);
            validator.validate(sessionId);
//            ra.addFlashAttribute("success", "File uploaded! Session: " + sessionId.substring(0, 8) + "…");
            return "redirect:/view/" + sessionId;
        } catch (Exception e) {
            log.error("Upload failed", e);
            ra.addFlashAttribute("error", "Upload failed: " + e.getMessage());
            return "redirect:/";
        }
    }

    // ── Viewer ────────────────────────────────────────────────────────────────
    @GetMapping("/view/{sessionId}")
    public String view(@PathVariable String sessionId,
//                       @RequestParam(defaultValue = "0") int tab,
                       @RequestParam(required = false) Integer tab,
                       @RequestParam(required = false, defaultValue = "all") String filter,
                       Model model) {
        UploadSession session = sessionRepo.findBySessionId(sessionId)
                .orElseThrow(() -> new RuntimeException("Session not found: " + sessionId));

        long errors   = issueRepo.countBySessionIdAndSeverity(sessionId, "CRITICAL");
        long warnings = issueRepo.countBySessionIdAndSeverity(sessionId, "WARNING");

        // Apply filter
        var all = issueRepo.findBySessionId(sessionId);
        var filtered = switch (filter.toLowerCase()) {
            case "critical" -> all.stream().filter(i -> "CRITICAL".equals(i.getSeverity())).toList();
            case "warning"  -> all.stream().filter(i -> "WARNING".equals(i.getSeverity())).toList();
            default         -> all;
        };

        // Serialize all issues to JSON for client-side jsGrid (no AJAX needed)
        String issuesJson = "[]";
        try {
            issuesJson = new ObjectMapper().writeValueAsString(
                issueRepo.findBySessionId(sessionId)); // always full list for JS
        } catch (Exception e) {
            log.warn("Could not serialize issues to JSON: {}", e.getMessage());
        }

        boolean hasMandatoryErrors = all.stream()
                .anyMatch(i -> "TS-08".equals(i.getRuleId()));

        model.addAttribute("hasMandatoryErrors", hasMandatoryErrors);

        model.addAttribute("uploadSession",       session);

        List<String> enabledRuleDescriptions = new ArrayList<>();
        List<String> enabledRuleIds = new ArrayList<>();

        if (session.getEnabledRules() != null) {

            for (String ruleId : session.getEnabledRules().split(",")) {

                enabledRuleIds.add(ruleId);

                ValidationRule rule = ValidationRule.fromRuleId(ruleId);

                if (rule != null) {
                    enabledRuleDescriptions.add(
                            rule.getRuleId() + " - " + rule.getDescription()
                    );
                }
            }
        }

        model.addAttribute("enabledRules", enabledRuleDescriptions);
        model.addAttribute("enabledRuleIds", enabledRuleIds);
        model.addAttribute("ruleGroups", ruleCatalog.getGroups());

        // Lazy loading: ship only lightweight per-sheet metadata (name, index,
        // row/col counts). The grids themselves are fetched per tab via
        // /api/view/{sessionId}/sheet/{index}.
        var metas = sheetView.getSheetMetas(sessionId);
        model.addAttribute("sheetMetas",    metas);

        // ── Phased validation state ──────────────────────────────────────────
        String phase = session.getValidationPhase() == null ? "TIMESHEET"
                : session.getValidationPhase();
        boolean pivotUnlocked = "PIVOT".equalsIgnoreCase(phase) || "PROJECT_WISE".equalsIgnoreCase(phase);

        boolean projectWiseUnlocked = "PROJECT_WISE".equalsIgnoreCase(phase);
        boolean summaryUnlocked = "SUMMARY".equalsIgnoreCase(phase);
        boolean commercialUnlocked = "COMMERCIAL".equalsIgnoreCase(phase);
        long timesheetErrors = issueRepo
                .countBySessionIdAndSheetNameAndSeverity(sessionId, "Timesheet", "CRITICAL");
        int pivotTabIndex = metas.stream()
                .filter(m -> "Pivot".equalsIgnoreCase(m.getSheetName()))
                .map(m -> m.getSheetIndex()).filter(java.util.Objects::nonNull)
                .findFirst().orElse(-1);

        long pivotErrors =
        issueRepo.countBySessionIdAndSheetNameAndSeverity(
                sessionId,
                "Pivot",
                "CRITICAL");

        int projectWiseTabIndex = metas.stream()
            .filter(m -> "Projectwise".equalsIgnoreCase(m.getSheetName()))
            .map(m -> m.getSheetIndex())
            .filter(x -> x != null)
            .findFirst()
            .orElse(-1);

        model.addAttribute("validationPhase",     phase);
        model.addAttribute("pivotUnlocked",       pivotUnlocked);
        model.addAttribute("pivotErrors", pivotErrors);
        model.addAttribute("timesheetErrors",     timesheetErrors);
        model.addAttribute("pivotTabIndex",       pivotTabIndex);
        long projectWiseErrors =
        issueRepo.countBySessionIdAndSheetNameAndSeverity(
                sessionId,
                "Projectwise",
                "CRITICAL");

        model.addAttribute("projectWiseUnlocked", projectWiseUnlocked);
        model.addAttribute("projectWiseTabIndex", projectWiseTabIndex);
        model.addAttribute("projectWiseErrors", projectWiseErrors);

        long summaryErrors =
                issueRepo.countBySessionIdAndSheetNameAndSeverity(
                        sessionId,
                        "Summary",
                        "CRITICAL");

        int summaryTabIndex = metas.stream()
                .filter(m -> "Summary".equalsIgnoreCase(m.getSheetName()))
                .map(m -> m.getSheetIndex())
                .filter(x -> x != null)
                .findFirst()
                .orElse(-1);

        int commercialTabIndex = metas.stream()
                .filter(m -> "Commercial".equalsIgnoreCase(m.getSheetName()))
                .map(m -> m.getSheetIndex())
                .filter(x -> x != null)
                .findFirst()
                .orElse(-1);

        model.addAttribute("summaryUnlocked", summaryUnlocked);
        model.addAttribute("commercialUnlocked", commercialUnlocked);
        model.addAttribute("summaryErrors", summaryErrors);
        model.addAttribute("summaryTabIndex", summaryTabIndex);
        model.addAttribute("commercialTabIndex", commercialTabIndex);

        model.addAttribute("errorCount",    errors);
        model.addAttribute("warningCount",  warnings);
        model.addAttribute("allIssues",     filtered);      // server-side filtered list (kept for th:if checks)
        model.addAttribute("allIssuesJson", issuesJson);    // full JSON for jsGrid

        if (tab == null) {

            tab = metas.stream()
                    .filter(m ->
                            "Timesheet".equalsIgnoreCase(
                                    m.getSheetName()))
                    .map(m -> m.getSheetIndex())
                    .findFirst()
                    .orElse(0);
        }


        model.addAttribute("activeTab",     tab);
        return "pages/viewer";
    }

    // ── Phased validation: advance Timesheet → Pivot ──────────────────────────
    @PostMapping("/view/{sessionId}/advance")
    public String advancePhase(
            @PathVariable String sessionId,
            RedirectAttributes ra) {

        UploadSession session = sessionRepo.findBySessionId(sessionId)
                .orElseThrow(() -> new RuntimeException("Session not found"));

        String phase = session.getValidationPhase();

        /*
        * ===========================================================
        * TIMESHEET  ->  PIVOT
        * ===========================================================
        */
        if ("TIMESHEET".equalsIgnoreCase(phase)) {

            long tsErrors = issueRepo.countBySessionIdAndSheetNameAndSeverity(
                    sessionId,
                    "Timesheet",
                    "CRITICAL");

            if (tsErrors > 0) {

                ra.addFlashAttribute(
                        "error",
                        "Cannot proceed to Pivot validation — resolve "
                                + tsErrors
                                + " unresolved Timesheet error(s) first.");

                return "redirect:/view/" + sessionId;
            }

            session.setValidationPhase("PIVOT");
            sessionRepo.save(session);

            ValidationResultDto result = validator.validate(sessionId);

            ra.addFlashAttribute(
                    "success",
                    "Timesheet passed. Pivot validation unlocked — found "
                            + result.getErrorCount() + " pivot error(s).");

            int pivotTab = sheetView.getSheetMetas(sessionId)
                    .stream()
                    .filter(m -> "Pivot".equalsIgnoreCase(m.getSheetName()))
                    .map(m -> m.getSheetIndex())
                    .filter(java.util.Objects::nonNull)
                    .findFirst().orElse(0);

            return "redirect:/view/" + sessionId + "?tab=" + pivotTab;
        }

        /*
        * ===========================================================
        * PIVOT  ->  PROJECT_WISE
        * ===========================================================
        */
        if ("PIVOT".equalsIgnoreCase(phase)) {

            long pivotErrors = issueRepo.countBySessionIdAndSheetNameAndSeverity(
                    sessionId, "Pivot", "CRITICAL");

            if (pivotErrors > 0) {

                ra.addFlashAttribute(
                        "error",
                        "Cannot proceed to Projectwise validation — resolve "
                                + pivotErrors + " unresolved Pivot error(s) first.");

                return "redirect:/view/" + sessionId;
            }

            session.setValidationPhase("PROJECT_WISE");
            sessionRepo.save(session);

            ValidationResultDto result = validator.validate(sessionId);

            ra.addFlashAttribute(
                    "success",
                    "Pivot passed. Projectwise validation unlocked — found "
                            + result.getErrorCount() + " Projectwise error(s).");

            int projectWiseTab = sheetView.getSheetMetas(sessionId)
                    .stream()
                    .filter(m -> "Projectwise".equalsIgnoreCase(m.getSheetName()))
                    .map(m -> m.getSheetIndex())
                    .filter(java.util.Objects::nonNull)
                    .findFirst().orElse(0);

            return "redirect:/view/" + sessionId + "?tab=" + projectWiseTab;
        }

        /*
        * ===========================================================
        * PROJECT_WISE  ->  SUMMARY
        * ===========================================================
        */
        if ("PROJECT_WISE".equalsIgnoreCase(phase)) {

            long projectWiseErrors = issueRepo.countBySessionIdAndSheetNameAndSeverity(
                    sessionId, "Projectwise", "CRITICAL");

            if (projectWiseErrors > 0) {

                ra.addFlashAttribute(
                        "error",
                        "Cannot proceed to Summary validation — resolve "
                                + projectWiseErrors + " unresolved Projectwise error(s) first.");

                return "redirect:/view/" + sessionId;
            }

            session.setValidationPhase("SUMMARY");
            sessionRepo.save(session);

            ValidationResultDto summaryResult = validator.validate(sessionId);

            ra.addFlashAttribute(
                    "success",
                    "Projectwise passed. Summary validation unlocked — found "
                            + summaryResult.getErrorCount() + " Summary error(s).");

            int summaryTab = sheetView.getSheetMetas(sessionId)
                    .stream()
                    .filter(m -> "Summary".equalsIgnoreCase(m.getSheetName()))
                    .map(m -> m.getSheetIndex())
                    .filter(java.util.Objects::nonNull)
                    .findFirst().orElse(0);

            return "redirect:/view/" + sessionId + "?tab=" + summaryTab;
        }

        /*
        * ===========================================================
        * SUMMARY  ->  COMMERCIAL (placeholder)
        * ===========================================================
        */
        if ("SUMMARY".equalsIgnoreCase(phase)) {

            long summaryErrors = issueRepo.countBySessionIdAndSheetNameAndSeverity(
                    sessionId, "Summary", "CRITICAL");

            if (summaryErrors > 0) {

                ra.addFlashAttribute(
                        "error",
                        "Cannot proceed to Commercial validation — resolve "
                                + summaryErrors + " unresolved Summary error(s) first.");

                return "redirect:/view/" + sessionId;
            }

            session.setValidationPhase("COMMERCIAL");
            sessionRepo.save(session);

            ra.addFlashAttribute(
                    "success",
                    "Summary passed. Commercial sheet is now accessible.");

            int commercialTab = sheetView.getSheetMetas(sessionId)
                    .stream()
                    .filter(m -> "Commercial".equalsIgnoreCase(m.getSheetName()))
                    .map(m -> m.getSheetIndex())
                    .filter(java.util.Objects::nonNull)
                    .findFirst().orElse(0);

            return "redirect:/view/" + sessionId + "?tab=" + commercialTab;
        }

        // Fallback: should not reach here
        ra.addFlashAttribute("error", "Unknown validation phase: " + phase);
        return "redirect:/view/" + sessionId;
    }

    // ── Phased validation: go back to the Timesheet phase ─────────────────────
    @PostMapping("/view/{sessionId}/reset-phase")
    public String resetPhase(@PathVariable String sessionId, RedirectAttributes ra) {
        UploadSession session = sessionRepo.findBySessionId(sessionId)
                .orElseThrow(() -> new RuntimeException("Session not found"));
        String currentPhase = session.getValidationPhase();
        if ("SUMMARY".equalsIgnoreCase(currentPhase)) {
            session.setValidationPhase("PROJECT_WISE");
        } else if ("COMMERCIAL".equalsIgnoreCase(currentPhase)) {
            session.setValidationPhase("SUMMARY");
        } else {
            session.setValidationPhase("TIMESHEET");
        }
        sessionRepo.save(session);
        validator.validate(sessionId);
        ra.addFlashAttribute("success", "Back to Timesheet validation.");
        return "redirect:/view/" + sessionId;
    }

    // ── Re-validate ───────────────────────────────────────────────────────────
    @PostMapping("/validate/{sessionId}")
    public String validate(@PathVariable String sessionId, RedirectAttributes ra) {

        log.info("REVALIDATE CLICKED FOR SESSION = {}", sessionId);

        ValidationResultDto result = validator.validate(sessionId);

        log.info("REVALIDATION COMPLETED. Errors={} Warnings={}",
                result.getErrorCount(),
                result.getWarningCount());

        ra.addFlashAttribute(result.isPassed() ? "success" : "warning",
                result.isPassed() ? "Validation passed — no critical errors."
                        : "Found " + result.getErrorCount() + " error(s) and "
                          + result.getWarningCount() + " warning(s).");
        return "redirect:/view/" + sessionId;
    }


    //update rules
    @PostMapping("/update-rules/{sessionId}")
    public String updateRules(@PathVariable String sessionId,
                              @RequestParam(required = false)
                              List<String> rules,
                              RedirectAttributes ra) {

        log.info("UPDATE RULES CALLED");
        log.info("RULES RECEIVED = {}", rules);

        UploadSession session = sessionRepo.findBySessionId(sessionId)
                .orElseThrow(() ->
                        new RuntimeException("Session not found"));

        if (rules == null || rules.isEmpty()) {
            ra.addFlashAttribute("error",
                    "Please select at least one validation rule.");
            return "redirect:/view/" + sessionId;
        }

        session.setEnabledRules(String.join(",", rules));

        sessionRepo.save(session);

        ValidationResultDto result =
                validator.validate(sessionId);

        ra.addFlashAttribute(
                result.isPassed() ? "success" : "warning",
                result.isPassed()
                        ? "Validation passed."
                        : "Validation completed. Found "
                        + result.getErrorCount()
                        + " errors and "
                        + result.getWarningCount()
                        + " warnings."
        );

        return "redirect:/view/" + sessionId;
    }

    // ── Export Issues as CSV ──────────────────────────────────────────────────
    // NOTE: URL uses /export-csv/ to avoid the // path segment that Spring
    // Security rejects when using /export/{id}/issues.csv  pattern.
    @GetMapping("/export-csv/{sessionId}")
    public void exportCsv(@PathVariable String sessionId,
                          @RequestParam(required = false, defaultValue = "all") String filter,
                          HttpServletResponse response) throws IOException {
        response.setContentType("text/csv;charset=UTF-8");
        response.setHeader("Content-Disposition",
                "attachment; filename=\"issues-" + sessionId.substring(0, 8) + ".csv\"");

        var all = issueRepo.findBySessionId(sessionId);
        var issues = switch (filter.toLowerCase()) {
            case "critical" -> all.stream().filter(i -> "CRITICAL".equals(i.getSeverity())).toList();
            case "warning"  -> all.stream().filter(i -> "WARNING".equals(i.getSeverity())).toList();
            default         -> all;
        };

        try (PrintWriter pw = new PrintWriter(
                new OutputStreamWriter(response.getOutputStream(), StandardCharsets.UTF_8))) {
            pw.println("Rule ID,Severity,Sheet,Row,Field,Message");
            for (var issue : issues) {
                pw.printf("\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\"%n",
                        csv(issue.getRuleId()), csv(issue.getSeverity()),
                        csv(issue.getSheetName()),
                        (issue.getRowIdx() != null && issue.getRowIdx() >= 0
                                ? "Row " + (issue.getRowIdx() + 1) : "Multiple"),
                        csv(issue.getFieldName()), csv(issue.getMessage()));
            }
        }
    }

    // ── Login page ────────────────────────────────────────────────────────────
    @GetMapping("/login")
    public String login() { return "pages/login"; }

    private String csv(String s) {
        return s == null ? "" : s.replace("\"", "\"\"");
    }

    @GetMapping("/403")
    public String forbidden() {
        return "pages/403";
    }

}