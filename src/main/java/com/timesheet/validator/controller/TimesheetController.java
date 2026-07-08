package com.timesheet.validator.controller;

import com.timesheet.validator.config.AppProperties;
import com.timesheet.validator.domain.AppUser;
import com.timesheet.validator.domain.TimesheetEntry;
import com.timesheet.validator.dto.TimesheetEntryForm;
import com.timesheet.validator.domain.SowMaster;
import com.timesheet.validator.repository.*;
import com.timesheet.validator.service.TimesheetEntryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.validation.Valid;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/timesheet")
@RequiredArgsConstructor
@Slf4j
public class TimesheetController {

    private final TimesheetEntryService entryService;
    private final ResourceRepository    resourceRepo;
    private final PublicHolidayRepository holidayRepo;
    private final AppUserRepository     userRepo;
    private final AppProperties         props;
    private final SowMasterRepository   sowRepo;
    private final ResourceSowRepository resourceSowRepo;

    // ── Main form page ────────────────────────────────────────────────────────
    @GetMapping
    public String form(Model model, @AuthenticationPrincipal UserDetails principal) {
        TimesheetEntryForm form = new TimesheetEntryForm();
        // SOW is now selected by user from the dropdown — not pre-filled
        form.setCompany(props.getSow().getClient());
        form.setCountryCode("IN");
        model.addAttribute("form", form);
        populateModel(model, principal.getUsername(), LocalDate.now());
        return "pages/timesheet-entry";
    }

    // ── Switch active date (AJAX-friendly redirect) ───────────────────────────
    @GetMapping("/day")
    public String day(@RequestParam String date,
                      Model model,
                      @AuthenticationPrincipal UserDetails principal) {
        LocalDate activeDate = entryService.parseDate(date);
        if (activeDate == null) activeDate = LocalDate.now();
        TimesheetEntryForm form = new TimesheetEntryForm();
        form.setEntryDate(date);
        // SOW is now selected by user from the dropdown — not pre-filled
        form.setCompany(props.getSow().getClient());
        form.setCountryCode("IN");
        model.addAttribute("form", form);
        populateModel(model, principal.getUsername(), activeDate);
        return "pages/timesheet-entry";
    }

    // ── Submit one task entry ─────────────────────────────────────────────────
    @PostMapping
    public String submit(@Valid @ModelAttribute("form") TimesheetEntryForm form,
                         BindingResult bindingResult,
                         @AuthenticationPrincipal UserDetails principal,
                         Model model,
                         RedirectAttributes ra) {

        String username     = principal.getUsername();
        String resourceName = resolveResourceName(username);
        LocalDate activeDate = entryService.parseDate(form.getEntryDate());
        if (activeDate == null) activeDate = LocalDate.now();

        if (bindingResult.hasErrors()) {
            populateModel(model, username, activeDate);
            return "pages/timesheet-entry";
        }

        List<String> errors = entryService.validate(form, resourceName);
        if (!errors.isEmpty()) {
            populateModel(model, username, activeDate);
            model.addAttribute("validationErrors", errors);
            return "pages/timesheet-entry";
        }

        TimesheetEntry saved = entryService.save(form, username, resourceName);
        BigDecimal total = entryService.getLoggedHours(resourceName, activeDate);
        double max = props.getValidation().getMaxHoursPerDay();

        ra.addFlashAttribute("success",
            "Task saved — " + saved.getHours() + " hrs on " + activeDate +
            ". Day total: " + total + " / " + max + " hrs.");
        return "redirect:/timesheet/day?date=" + activeDate;
    }

    // ── Delete a single task entry ────────────────────────────────────────────
    @PostMapping("/delete/{id}")
    public String delete(@PathVariable Long id,
                         @RequestParam(required = false) String date,
                         @AuthenticationPrincipal UserDetails principal,
                         RedirectAttributes ra) {
        entryService.deleteEntry(id);
        ra.addFlashAttribute("success", "Task entry removed.");
        String redirect = (date != null && !date.isBlank())
                ? "redirect:/timesheet/day?date=" + date
                : "redirect:/timesheet";
        return redirect;
    }

    // ── AJAX: remaining hours for a date ──────────────────────────────────────
    @GetMapping("/api/remaining")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> remaining(
            @RequestParam String date,
            @AuthenticationPrincipal UserDetails principal) {
        String resourceName = resolveResourceName(principal.getUsername());
        LocalDate d = entryService.parseDate(date);
        if (d == null) return ResponseEntity.badRequest().build();
        BigDecimal logged = entryService.getLoggedHours(resourceName, d);
        double max = props.getValidation().getMaxHoursPerDay();
        List<TimesheetEntry> tasks = entryService.getTasksForDay(resourceName, d);
        return ResponseEntity.ok(Map.of(
            "logged",    logged,
            "max",       max,
            "remaining", BigDecimal.valueOf(max).subtract(logged),
            "taskCount", tasks.size()
        ));
    }

    // ── My entries list ───────────────────────────────────────────────────────
    @GetMapping("/my-entries")
    public String myEntries(Model model, @AuthenticationPrincipal UserDetails principal) {
        String username = principal.getUsername();
        String resourceName = resolveResourceName(username);
        model.addAttribute("entries",     entryService.getMyEntries(username));
        model.addAttribute("username",    username);
        model.addAttribute("resourceName", resourceName);
        model.addAttribute("dailySummary", entryService.getDailySummary(username));
        model.addAttribute("maxHours",    props.getValidation().getMaxHoursPerDay());
        return "pages/my-entries";
    }

    // ── All entries (manager / admin) ─────────────────────────────────────────
    @GetMapping("/all-entries")
    public String allEntries(Model model) {
        model.addAttribute("entries", entryService.getAllEntries());
        return "pages/all-entries";
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private void populateModel(Model model, String username, LocalDate activeDate) {
        String resourceName = resolveResourceName(username);
        BigDecimal logged   = entryService.getLoggedHours(resourceName, activeDate);
        double max          = props.getValidation().getMaxHoursPerDay();
        List<TimesheetEntry> dayTasks = entryService.getTasksForDay(resourceName, activeDate);

        model.addAttribute("resources",         resourceRepo.findAll());
        model.addAttribute("holidays",          holidayRepo.findAll());
        model.addAttribute("maxHours",          max);
        model.addAttribute("expectedSow",       props.getValidation().getExpectedSow());
        model.addAttribute("sowClient",         props.getSow().getClient());
        model.addAttribute("currentUsername",   username);
        model.addAttribute("resourceName",      resourceName);
        model.addAttribute("activeDate",        activeDate.toString());
        model.addAttribute("loggedHours",       logged);
        model.addAttribute("remainingHours",    BigDecimal.valueOf(max).subtract(logged));
        model.addAttribute("dayTasks",          dayTasks);
        model.addAttribute("overrideReasons",   props.getValidation().getWeekendOverrideReasons());
        model.addAttribute("projectCodes",      List.of(
            "529NF.40.02","00IW2","529MA.03.01","529NF.50.02","55Q30"));
        model.addAttribute("projects",          List.of(
            "Australia Maintenance", "Qatar Maintenance"));
        model.addAttribute("myEntries",         entryService.getMyEntries(username));
        // SOWs available to this resource — used for the SOW dropdown in the form
        List<SowMaster> availableSows = resolveAvailableSows(resourceName);
        model.addAttribute("availableSows",     availableSows);
    }

    private String resolveResourceName(String username) {
        return userRepo.findByUsername(username)
            .map(AppUser::getResourceId)
            .flatMap(rid -> resourceRepo.findAll().stream()
                .filter(r -> rid != null && rid.equals(r.getResourceId()))
                .findFirst())
            .map(r -> r.getName())
            .orElseGet(() ->
                resourceRepo.findAll().stream()
                    .filter(r -> {
                        String c  = r.getName().toLowerCase().replaceAll("[^a-z0-9]", "");
                        String uc = username.toLowerCase().replaceAll("[^a-z0-9]", "");
                        int len   = Math.min(c.length(), Math.min(uc.length(), 6));
                        return len > 0 && c.startsWith(uc.substring(0, len));
                    })
                    .findFirst().map(r -> r.getName()).orElse(username)
            );
    }

    /**
     * Returns all active SOWs that are mapped to this resource.
     * Falls back to all active SOWs if no specific mapping is found.
     */
    private List<SowMaster> resolveAvailableSows(String resourceName) {
        // Find resourceId for this resource name
        return resourceRepo.findAll().stream()
            .filter(r -> r.getName().equals(resourceName))
            .findFirst()
            .map(r -> {
                List<String> sowNums = resourceSowRepo.findSowNumbersByResourceId(r.getResourceId());
                if (sowNums.isEmpty()) return sowRepo.findByActiveTrue();
                return sowNums.stream()
                    .map(sowRepo::findBySowNumber)
                    .filter(java.util.Optional::isPresent)
                    .map(java.util.Optional::get)
                    .filter(s -> Boolean.TRUE.equals(s.getActive()))
                    .collect(java.util.stream.Collectors.toList());
            })
            .orElseGet(() -> sowRepo.findByActiveTrue());
    }

}