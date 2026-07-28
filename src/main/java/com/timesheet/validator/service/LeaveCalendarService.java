package com.timesheet.validator.service;

import com.timesheet.validator.domain.LeaveEntry;
import com.timesheet.validator.domain.PublicHoliday;
import com.timesheet.validator.domain.Resource;
import com.timesheet.validator.dto.CalendarEventDto;
import com.timesheet.validator.repository.AppUserRepository;
import com.timesheet.validator.repository.LeaveEntryRepository;
import com.timesheet.validator.repository.PublicHolidayRepository;
import com.timesheet.validator.repository.ResourceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Everything the /calendar screen needs: resolving the signed-in user to
 * a Resource, self-service register/cancel of leave, and the merged feed
 * of public holidays + own leaves + team leaves for a date window.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class LeaveCalendarService {

    private final AppUserRepository      userRepo;
    private final ResourceRepository     resourceRepo;
    private final LeaveEntryRepository   leaveRepo;
    private final PublicHolidayRepository holidayRepo;

    // ── current user → resource ──────────────────────────────────────────────

    /**
     * Resolve the signed-in username to a Resource. Tries the explicit
     * APP_USER.RESOURCE_ID link first, then falls back to matching a
     * resource by normalised name. Empty if the account isn't tied to a
     * roster resource (e.g. a plain admin) — such users can still view the
     * calendar but cannot register leave.
     */
    public Optional<Resource> resolveResource(String username) {
        Optional<Resource> byId = userRepo.findByUsername(username)
                .map(u -> u.getResourceId())
                .filter(Objects::nonNull)
                .flatMap(rid -> resourceRepo.findAll().stream()
                        .filter(r -> rid.equals(r.getResourceId()))
                        .findFirst());
        if (byId.isPresent()) return byId;

        String key = normalize(username);
        return resourceRepo.findAll().stream()
                .filter(r -> normalize(r.getName()).equals(key))
                .findFirst();
    }

    /**
     * A leave "owner" for the signed-in user. Every authenticated user has one:
     * a roster resource when their account is linked, otherwise a synthetic
     * identity keyed on the username. This lets anyone (including admin) add and
     * delete their own leave, which is visible to everyone via the feed.
     */
    public record LeaveIdentity(String key, String name, boolean linked) {}

    public LeaveIdentity resolveIdentity(String username) {
        Optional<Resource> res = resolveResource(username);
        if (res.isPresent()) {
            return new LeaveIdentity(res.get().getResourceId(), res.get().getName(), true);
        }
        String display = userRepo.findByUsername(username)
                .map(u -> u.getFullName())
                .filter(n -> n != null && !n.isBlank())
                .orElse(username);
        // "USER:" prefix keeps synthetic keys from colliding with real resource ids.
        return new LeaveIdentity("USER:" + username, display, false);
    }

    // ── register / cancel (self-service, instant) ────────────────────────────

    public static class RegisterResult {
        public int added;
        public int skippedWeekend;
        public int skippedHoliday;
        public int skippedExisting;
    }

    /**
     * Register leave for every working day in [start, end] for the given
     * resource. Weekends and public holidays are skipped (you're already
     * off), as are days already marked. Self-service → immediately active.
     */
    @Transactional
    public RegisterResult registerLeave(LeaveIdentity who, LocalDate start, LocalDate end,
                                        String leaveType, String reason, String username) {
        RegisterResult r = new RegisterResult();
        if (start == null || end == null || end.isBefore(start)) return r;

        Set<LocalDate> holidays = activeHolidayDates();

        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            DayOfWeek dow = d.getDayOfWeek();
            if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) { r.skippedWeekend++; continue; }
            if (holidays.contains(d)) { r.skippedHoliday++; continue; }
            if (leaveRepo.existsByResourceIdAndLeaveDateAndSource(
                    who.key(), d, LeaveEntry.SOURCE_USER)) { r.skippedExisting++; continue; }

            leaveRepo.save(LeaveEntry.builder()
                    .resourceId(who.key())
                    .resourceName(who.name())
                    .leaveDate(d)
                    .leaveType(leaveType == null || leaveType.isBlank() ? "FULL_DAY" : leaveType)
                    .status(LeaveEntry.STATUS_APPROVED)
                    .source(LeaveEntry.SOURCE_USER)
                    .reason(reason)
                    .createdBy(username)
                    .createdAt(LocalDateTime.now())
                    .build());
            r.added++;
        }
        return r;
    }

    /** Cancel one of the user's OWN self-registered leaves. Returns false if it isn't theirs. */
    @Transactional
    public boolean cancelOwnLeave(Long leaveId, LeaveIdentity who) {
        return leaveRepo.findById(leaveId)
                .filter(l -> LeaveEntry.SOURCE_USER.equals(l.getSource()))
                .filter(l -> who.key().equals(l.getResourceId()))
                .map(l -> { leaveRepo.delete(l); return true; })
                .orElse(false);
    }

    // ── merged feed ──────────────────────────────────────────────────────────

    /**
     * Build the calendar feed for [from, to]:
     * public holidays (read-only), the user's own leaves (editable),
     * and everyone else's leaves (read-only team markers).
     */
    public List<CalendarEventDto> feed(LocalDate from, LocalDate to, LeaveIdentity me) {
        List<CalendarEventDto> events = new ArrayList<>();

        // Public holidays (enabled only)
        holidayRepo.findAll().stream()
                .filter(PublicHoliday::isActive)
                .filter(h -> inRange(h.getHolidayDate(), from, to))
                .forEach(h -> events.add(CalendarEventDto.builder()
                        .kind(CalendarEventDto.KIND_HOLIDAY)
                        .date(h.getHolidayDate().toString())
                        .title(h.getHolidayName() == null ? "Public Holiday" : h.getHolidayName())
                        .subtitle(h.getCountryCode())
                        .editable(false)
                        .build()));

        String myKey = me == null ? null : me.key();

        for (LeaveEntry l : leaveRepo.findByLeaveDateBetweenOrderByLeaveDateAsc(from, to)) {
            boolean mine = myKey != null && myKey.equals(l.getResourceId());
            boolean editable = mine && LeaveEntry.SOURCE_USER.equals(l.getSource());
            events.add(CalendarEventDto.builder()
                    .kind(mine ? CalendarEventDto.KIND_MY_LEAVE : CalendarEventDto.KIND_TEAM_LEAVE)
                    .date(l.getLeaveDate().toString())
                    .title(mine ? "My Leave" : l.getResourceName())
                    .subtitle(subtitle(l))
                    .leaveId(editable ? l.getId() : null)
                    .editable(editable)
                    .source(l.getSource())
                    .build());
        }
        return events;
    }

    private String subtitle(LeaveEntry l) {
        String t = l.getLeaveType() == null ? "Leave" : l.getLeaveType();
        return LeaveEntry.SOURCE_ADMIN.equals(l.getSource()) ? t + " (Planner)" : t;
    }

    private Set<LocalDate> activeHolidayDates() {
        Set<LocalDate> s = new HashSet<>();
        holidayRepo.findAll().stream()
                .filter(PublicHoliday::isActive)
                .forEach(h -> s.add(h.getHolidayDate()));
        return s;
    }

    private boolean inRange(LocalDate d, LocalDate from, LocalDate to) {
        return d != null && !d.isBefore(from) && !d.isAfter(to);
    }

    private String normalize(String s) {
        return s == null ? "" : s.trim().replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT);
    }
}
