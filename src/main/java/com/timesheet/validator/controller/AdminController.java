package com.timesheet.validator.controller;

import com.timesheet.validator.config.AppProperties;
import com.timesheet.validator.domain.*;
import com.timesheet.validator.config.RuleCatalog;
import com.timesheet.validator.repository.*;
import com.timesheet.validator.security.AppUserDetailsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
@Slf4j
public class AdminController {

    private final ResourceRepository     resourceRepo;
    private final PublicHolidayRepository holidayRepo;
    private final AppUserRepository      userRepo;
    private final RoleRepository         roleRepo;
    private final PasswordEncoder        passwordEncoder;
    private final RuleCatalog            ruleCatalog;
    private final AppProperties props;

    // ══════════════════════════════════════════════════════════════════════════
    // VALIDATION RULES (enable / disable from DB)
    // ══════════════════════════════════════════════════════════════════════════

    @GetMapping("/rules")
    public String rules(Model model) {
        model.addAttribute("ruleGroups", ruleCatalog.getAllRulesBySheet());
        return "pages/admin/rules";
    }

    @PostMapping("/rules/toggle/{ruleId}")
    public String toggleRule(@PathVariable String ruleId,
                             @RequestParam(defaultValue = "true") boolean enabled,
                             RedirectAttributes ra) {
        ruleCatalog.setEnabled(ruleId, enabled);
        ra.addFlashAttribute("success",
                "Rule '" + ruleId + "' " + (enabled ? "enabled" : "disabled") + ".");
        return "redirect:/admin/rules";
    }

    @GetMapping("/rules/new")
    public String newRule(Model model) {
        RuleConfig rc = new RuleConfig();
        rc.setEnabled(true);
        rc.setAlwaysOn(false);
        rc.setSeverity("CRITICAL");
        rc.setSortOrder(0);
        model.addAttribute("rule", rc);
        model.addAttribute("editMode", false);
        return "pages/admin/rule-form";
    }

    @GetMapping("/rules/edit/{id}")
    public String editRule(@PathVariable Long id, Model model) {
        RuleConfig rc = ruleCatalog.getRule(id)
                .orElseThrow(() -> new RuntimeException("Rule not found: " + id));
        model.addAttribute("rule", rc);
        model.addAttribute("editMode", true);
        return "pages/admin/rule-form";
    }

    @PostMapping("/rules/save")
    public String saveRule(
            @RequestParam(required = false) Long id,
            @RequestParam String ruleId,
            @RequestParam(required = false) String sheetName,
            @RequestParam(required = false) String description,
            @RequestParam(required = false, defaultValue = "CRITICAL") String severity,
            @RequestParam(required = false, defaultValue = "false") boolean alwaysOn,
            @RequestParam(required = false, defaultValue = "false") boolean enabled,
            @RequestParam(required = false, defaultValue = "0") int sortOrder,
            @RequestParam(required = false) String messageTemplate,
            RedirectAttributes ra) {

        String rid = ruleId == null ? "" : ruleId.trim();
        if (rid.isEmpty()) {
            ra.addFlashAttribute("error", "Rule ID is required.");
            return "redirect:/admin/rules/new";
        }
        // Guard against duplicate rule IDs on create
        if (id == null && ruleCatalog.ruleIdExists(rid)) {
            ra.addFlashAttribute("error", "Rule ID '" + rid + "' already exists.");
            return "redirect:/admin/rules/new";
        }

        RuleConfig rc = id != null
                ? ruleCatalog.getRule(id).orElseGet(RuleConfig::new)
                : new RuleConfig();
        rc.setRuleId(rid);
        rc.setSheetName(sheetName);
        rc.setDescription(description);
        rc.setSeverity(severity);
        rc.setAlwaysOn(alwaysOn);
        rc.setEnabled(enabled);
        rc.setSortOrder(sortOrder);
        rc.setMessageTemplate(messageTemplate != null && messageTemplate.isBlank() ? null : messageTemplate);
        ruleCatalog.saveRule(rc);

        ra.addFlashAttribute("success", "Rule '" + rc.getRuleId() + "' saved.");
        return "redirect:/admin/rules";
    }

    @PostMapping("/rules/delete/{id}")
    public String deleteRule(@PathVariable Long id, RedirectAttributes ra) {
        ruleCatalog.deleteRule(id);
        ra.addFlashAttribute("success", "Rule deleted.");
        return "redirect:/admin/rules";
    }

    // ── Dashboard ─────────────────────────────────────────────────────────────
    @GetMapping
    public String dashboard(Model model) {
        model.addAttribute("resourceCount", resourceRepo.count());
        model.addAttribute("holidayCount",  holidayRepo.count());
        model.addAttribute("userCount",     userRepo.count());
        model.addAttribute("roleCount",     roleRepo.count());
        return "pages/admin/dashboard";
    }

    // ══════════════════════════════════════════════════════════════════════════
    // RESOURCES
    // ══════════════════════════════════════════════════════════════════════════

    @GetMapping("/resources")
    public String resources(Model model) {
        model.addAttribute("resources", resourceRepo.findAll());
        return "pages/admin/resources";
    }

    @GetMapping("/resources/new")
    public String newResource(Model model) {
        model.addAttribute("resource", new Resource());
        model.addAttribute("editMode", false);
        return "pages/admin/resource-form";
    }

    @GetMapping("/resources/edit/{id}")
    public String editResource(@PathVariable Long id, Model model) {
        Resource r = resourceRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Resource not found: " + id));
        model.addAttribute("resource", r);
        model.addAttribute("editMode", true);
        return "pages/admin/resource-form";
    }

    @PostMapping("/resources/save")
    public String saveResource(
            @RequestParam(required = false) Long id,
            @RequestParam String resourceId,
            @RequestParam String name,
            @RequestParam(required = false) String dailyRateUsd,
            @RequestParam(required = false) Double workingHoursPerDay,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            RedirectAttributes ra) {
        Resource r = id != null ? resourceRepo.findById(id).orElse(new Resource()) : new Resource();
        r.setResourceId(resourceId.trim());
        r.setName(name.trim());
        r.setWorkingHoursPerDay(
                workingHoursPerDay != null
                        ? workingHoursPerDay
                        : props.getDefaultWorkingHoursPerDay()
        );
        if (dailyRateUsd != null && !dailyRateUsd.isBlank()) {
            try { r.setDailyRateUsd(new BigDecimal(dailyRateUsd.trim())); }
            catch (NumberFormatException ignored) {}
        }
        r.setStartDate(startDate);
        r.setEndDate(endDate);
        resourceRepo.save(r);
        ra.addFlashAttribute("success", "Resource '" + r.getName() + "' saved.");
        return "redirect:/admin/resources";
    }

    @PostMapping("/resources/delete/{id}")
    public String deleteResource(@PathVariable Long id, RedirectAttributes ra) {
        resourceRepo.findById(id).ifPresent(r -> {
            resourceRepo.delete(r);
            ra.addFlashAttribute("success", "Resource '" + r.getName() + "' deleted.");
        });
        return "redirect:/admin/resources";
    }

    // ══════════════════════════════════════════════════════════════════════════
    // HOLIDAYS
    // ══════════════════════════════════════════════════════════════════════════

    @GetMapping("/holidays")
    public String holidays(Model model) {
        model.addAttribute("holidays", holidayRepo.findAll());
        return "pages/admin/holidays";
    }

    @GetMapping("/holidays/new")
    public String newHoliday(Model model) {
        model.addAttribute("holiday", new PublicHoliday());
        model.addAttribute("editMode", false);
        return "pages/admin/holiday-form";
    }

    @GetMapping("/holidays/edit/{id}")
    public String editHoliday(@PathVariable Long id, Model model) {
        PublicHoliday h = holidayRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Holiday not found: " + id));
        model.addAttribute("holiday", h);
        model.addAttribute("editMode", true);
        return "pages/admin/holiday-form";
    }

    @PostMapping("/holidays/save")
    public String saveHoliday(
            @RequestParam(required = false) Long id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate holidayDate,
            @RequestParam String holidayName,
            @RequestParam(required = false, defaultValue = "IN") String countryCode,
            @RequestParam(required = false) String notes,
            RedirectAttributes ra) {
        PublicHoliday h = id != null ? holidayRepo.findById(id).orElse(new PublicHoliday()) : new PublicHoliday();
        h.setHolidayDate(holidayDate);
        h.setHolidayName(holidayName.trim());
        h.setCountryCode(countryCode.trim());
        h.setNotes(notes);
        holidayRepo.save(h);
        ra.addFlashAttribute("success", "Holiday '" + h.getHolidayName() + "' saved.");
        return "redirect:/admin/holidays";
    }

    @PostMapping("/holidays/delete/{id}")
    public String deleteHoliday(@PathVariable Long id, RedirectAttributes ra) {
        holidayRepo.findById(id).ifPresent(h -> {
            holidayRepo.delete(h);
            ra.addFlashAttribute("success", "Holiday '" + h.getHolidayName() + "' deleted.");
        });
        return "redirect:/admin/holidays";
    }

    @PostMapping("/holidays/toggle/{id}")
    public String toggleHoliday(@PathVariable Long id, RedirectAttributes ra) {
        holidayRepo.findById(id).ifPresent(h -> {
            h.setEnabled(!h.isActive());
            holidayRepo.save(h);
            ra.addFlashAttribute("success",
                "Holiday '" + h.getHolidayName() + "' " + (h.isActive() ? "enabled" : "disabled") + ".");
        });
        return "redirect:/admin/holidays";
    }

    // ══════════════════════════════════════════════════════════════════════════
    // USERS
    // ══════════════════════════════════════════════════════════════════════════

    @GetMapping("/users")
    public String users(Model model) {
        model.addAttribute("users", userRepo.findAll());
        model.addAttribute("roles", roleRepo.findAll());
        model.addAttribute("resources", resourceRepo.findAll());
        return "pages/admin/users";
    }

    @GetMapping("/users/new")
    public String newUser(Model model) {
        model.addAttribute("user", new AppUser());
        model.addAttribute("allRoles", roleRepo.findAll());
        model.addAttribute("resources", resourceRepo.findAll());
        model.addAttribute("editMode", false);
        return "pages/admin/user-form";
    }

    @GetMapping("/users/edit/{id}")
    public String editUser(@PathVariable Long id, Model model) {
        AppUser u = userRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found: " + id));
        model.addAttribute("user", u);
        model.addAttribute("allRoles", roleRepo.findAll());
        model.addAttribute("resources", resourceRepo.findAll());
        model.addAttribute("editMode", true);
        return "pages/admin/user-form";
    }

    @PostMapping("/users/save")
    public String saveUser(
            @RequestParam(required = false) Long id,
            @RequestParam String username,
            @RequestParam(required = false) String password,
            @RequestParam(required = false) String fullName,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String resourceId,
            @RequestParam(required = false, defaultValue = "true") boolean enabled,
            @RequestParam(required = false) Set<Long> roleIds,
            RedirectAttributes ra) {

        AppUser u = id != null ? userRepo.findById(id).orElse(new AppUser()) : new AppUser();
        u.setUsername(username.trim());
        if (password != null && !password.isBlank()) {
            u.setPassword(passwordEncoder.encode(password.trim()));
        }
        u.setFullName(fullName);
        u.setEmail(email);
        u.setResourceId(resourceId != null && !resourceId.isBlank() ? resourceId : null);
        u.setEnabled(enabled);

        if (roleIds != null && !roleIds.isEmpty()) {
            Set<Role> roles = new HashSet<>(roleRepo.findAllById(roleIds));
            u.setRoles(roles);
        }
        userRepo.save(u);
        ra.addFlashAttribute("success", "User '" + u.getUsername() + "' saved.");
        return "redirect:/admin/users";
    }

    @PostMapping("/users/toggle/{id}")
    public String toggleUser(@PathVariable Long id, RedirectAttributes ra) {
        userRepo.findById(id).ifPresent(u -> {
            u.setEnabled(!Boolean.TRUE.equals(u.getEnabled()));
            userRepo.save(u);
            ra.addFlashAttribute("success",
                "User '" + u.getUsername() + "' " + (u.getEnabled() ? "enabled" : "disabled") + ".");
        });
        return "redirect:/admin/users";
    }
}
