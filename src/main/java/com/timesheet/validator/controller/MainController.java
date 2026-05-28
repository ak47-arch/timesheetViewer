package com.timesheet.validator.controller;

import com.timesheet.validator.domain.UploadSession;
import com.timesheet.validator.dto.SheetDto;
import com.timesheet.validator.dto.ValidationResultDto;
import com.timesheet.validator.repository.PublicHolidayRepository;
import com.timesheet.validator.repository.ResourceRepository;
import com.timesheet.validator.repository.UploadSessionRepository;
import com.timesheet.validator.repository.ValidationIssueRepository;
import com.timesheet.validator.service.ExcelParserService;
import com.timesheet.validator.service.SheetViewService;
import com.timesheet.validator.service.ValidationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequiredArgsConstructor
@Slf4j
public class MainController {

    private final ExcelParserService parser;
    private final ValidationService  validator;
    private final SheetViewService   sheetView;
    private final UploadSessionRepository sessionRepo;
    private final ValidationIssueRepository issueRepo;
    private final PublicHolidayRepository holidayRepo;
    private final ResourceRepository resourceRepo;

    // ── Home / Upload ─────────────────────────────────────────────────────────
    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("sessions", sessionRepo.findAllByOrderByUploadedAtDesc());
        model.addAttribute("holidayCount", holidayRepo.count());
        model.addAttribute("resourceCount", resourceRepo.count());
        model.addAttribute("holidays", holidayRepo.findAll());
        return "pages/home";
    }

    @PostMapping("/upload")
    public String upload(@RequestParam("file") MultipartFile file,
                         RedirectAttributes ra) {
        if (file.isEmpty()) {
            ra.addFlashAttribute("error", "Please select an Excel (.xlsx) file to upload.");
            return "redirect:/";
        }
        String name = file.getOriginalFilename();
        if (name == null || !name.toLowerCase().endsWith(".xlsx")) {
            ra.addFlashAttribute("error", "Only .xlsx files are supported.");
            return "redirect:/";
        }
        try {
            String sessionId = parser.parse(file);
            // Auto-validate after upload
            validator.validate(sessionId);
            ra.addFlashAttribute("success", "File uploaded successfully! Session: " + sessionId.substring(0, 8) + "…");
            return "redirect:/view/" + sessionId;
        } catch (Exception e) {
            log.error("Upload failed", e);
            ra.addFlashAttribute("error", "Upload failed: " + e.getMessage());
            return "redirect:/";
        }
    }

    // ── Sheet Viewer ──────────────────────────────────────────────────────────
    @GetMapping("/view/{sessionId}")
    public String view(@PathVariable String sessionId,
                       @RequestParam(defaultValue = "0") int tab,
                       Model model) {
        UploadSession session = sessionRepo.findBySessionId(sessionId)
                .orElseThrow(() -> new RuntimeException("Session not found: " + sessionId));

        List<SheetDto> sheets = sheetView.getSheets(sessionId);

        long errors   = issueRepo.countBySessionIdAndSeverity(sessionId, "CRITICAL");
        long warnings = issueRepo.countBySessionIdAndSeverity(sessionId, "WARNING");

        model.addAttribute("session", session);
        model.addAttribute("sheets", sheets);
        model.addAttribute("activeTab", tab);
        model.addAttribute("errorCount", errors);
        model.addAttribute("warningCount", warnings);
        model.addAttribute("allIssues", issueRepo.findBySessionId(sessionId));
        return "pages/viewer";
    }

    // ── Re-validate ───────────────────────────────────────────────────────────
    @PostMapping("/validate/{sessionId}")
    public String validate(@PathVariable String sessionId, RedirectAttributes ra) {
        ValidationResultDto result = validator.validate(sessionId);
        if (result.isPassed()) {
            ra.addFlashAttribute("success", "Validation passed! No critical errors.");
        } else {
            ra.addFlashAttribute("warning",
                    "Validation found " + result.getErrorCount() + " error(s) and "
                    + result.getWarningCount() + " warning(s).");
        }
        return "redirect:/view/" + sessionId;
    }
}
