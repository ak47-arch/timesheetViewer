package com.timesheet.validator.config;

import com.timesheet.validator.domain.PublicHoliday;
import com.timesheet.validator.domain.Resource;
import com.timesheet.validator.repository.PublicHolidayRepository;
import com.timesheet.validator.repository.ResourceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Seeds master data (holidays, resources) from application.yml into H2.
 * Runs on every startup — idempotent (wipe + re-seed).
 * To update: edit application.yml and restart.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MasterDataLoader implements ApplicationRunner {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final AppProperties props;
    private final PublicHolidayRepository holidayRepo;
    private final ResourceRepository resourceRepo;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        log.info("=== [MasterDataLoader] Seeding from application.yml ===");
        seedHolidays();
        seedResources();
        log.info("=== [MasterDataLoader] Done. Holidays={} Resources={} ===",
                holidayRepo.count(), resourceRepo.count());
    }

    private LocalDate parse(String s) {
        if (s == null || s.isBlank()) return null;
        return LocalDate.parse(s.trim(), FMT);
    }

    private void seedHolidays() {
        holidayRepo.deleteAllInBatch();
        List<PublicHoliday> list = props.getHolidays().stream()
                .map(h -> PublicHoliday.builder()
                        .holidayDate(parse(h.getHolidayDate()))
                        .holidayName(h.getHolidayName())
                        .countryCode(h.getCountryCode())
                        .notes(h.getNotes())
                        .build())
                .collect(Collectors.toList());
        holidayRepo.saveAll(list);
        log.info("[MasterDataLoader] Holidays seeded: {}", list.size());
    }

    private void seedResources() {
        resourceRepo.deleteAllInBatch();
        List<Resource> list = props.getResources().stream()
                .map(r -> Resource.builder()
                        .resourceId(r.getResourceId())
                        .name(r.getName())
                        .dailyRateUsd(r.getDailyRateUsd())
                        .startDate(parse(r.getStartDate()))
                        .endDate(parse(r.getEndDate()))
                        .build())
                .collect(Collectors.toList());
        resourceRepo.saveAll(list);
        log.info("[MasterDataLoader] Resources seeded: {}", list.size());
    }
}
