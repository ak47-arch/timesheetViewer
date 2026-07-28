package com.timesheet.validator.controller;

import com.timesheet.validator.dto.CalendarEventDto;
import com.timesheet.validator.service.LeaveCalendarService;
import com.timesheet.validator.service.LeaveCalendarService.LeaveIdentity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

/**
 * User-facing leave calendar. Every authenticated user can view it and
 * add/delete their own leave (self-service, instant, visible to everyone).
 * Public holidays and admin-planner leaves are overlaid read-only.
 */
@Controller
@RequestMapping("/calendar")
@RequiredArgsConstructor
@Slf4j
public class CalendarController {

    private final LeaveCalendarService calendar;

    /** The month-grid page. */
    @GetMapping
    public String page(@RequestParam(required = false) Integer year,
                       @RequestParam(required = false) Integer month,
                       @AuthenticationPrincipal UserDetails principal,
                       Model model) {

        YearMonth ym = (year != null && month != null)
                ? YearMonth.of(year, month) : YearMonth.now();

        LeaveIdentity me = calendar.resolveIdentity(principal.getUsername());

        model.addAttribute("year",  ym.getYear());
        model.addAttribute("month", ym.getMonthValue());
        model.addAttribute("monthLabel", ym.getMonth() + " " + ym.getYear());
        model.addAttribute("canRegister", true);          // every signed-in user may register
        model.addAttribute("resourceName", me.name());
        return "pages/calendar";
    }

    /** JSON feed for a date window: holidays + own leaves + team leaves. */
    @GetMapping("/events")
    @ResponseBody
    public List<CalendarEventDto> events(@RequestParam String from,
                                         @RequestParam String to,
                                         @AuthenticationPrincipal UserDetails principal) {
        LeaveIdentity me = calendar.resolveIdentity(principal.getUsername());
        return calendar.feed(LocalDate.parse(from), LocalDate.parse(to), me);
    }

    /** Add own leave for a date (or range). Self-service → instant, visible to all. */
    @PostMapping("/leave")
    @ResponseBody
    public Map<String, Object> register(@RequestParam String startDate,
                                        @RequestParam(required = false) String endDate,
                                        @RequestParam(required = false) String leaveType,
                                        @RequestParam(required = false) String reason,
                                        @AuthenticationPrincipal UserDetails principal) {

        LeaveIdentity me = calendar.resolveIdentity(principal.getUsername());

        LocalDate start = LocalDate.parse(startDate);
        LocalDate end   = (endDate == null || endDate.isBlank()) ? start : LocalDate.parse(endDate);

        var r = calendar.registerLeave(me, start, end, leaveType, reason, principal.getUsername());
        return Map.of(
                "ok", true,
                "added", r.added,
                "skippedWeekend", r.skippedWeekend,
                "skippedHoliday", r.skippedHoliday,
                "skippedExisting", r.skippedExisting,
                "message", r.added + " day(s) added"
                        + (r.skippedWeekend + r.skippedHoliday + r.skippedExisting > 0
                           ? " · " + r.skippedWeekend + " weekend, " + r.skippedHoliday
                             + " holiday, " + r.skippedExisting + " already booked skipped"
                           : ""));
    }

    /** Delete one of the user's own self-registered leaves. */
    @PostMapping("/leave/delete/{id}")
    @ResponseBody
    public Map<String, Object> cancel(@PathVariable Long id,
                                      @AuthenticationPrincipal UserDetails principal) {
        LeaveIdentity me = calendar.resolveIdentity(principal.getUsername());
        boolean ok = calendar.cancelOwnLeave(id, me);
        return Map.of("ok", ok,
                "message", ok ? "Leave cancelled" : "You can only cancel your own registered leave.");
    }
}
