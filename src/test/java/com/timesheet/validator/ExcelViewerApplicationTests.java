package com.timesheet.validator;

import com.timesheet.validator.repository.PublicHolidayRepository;
import com.timesheet.validator.repository.ResourceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ExcelViewerApplicationTests {

    @Autowired PublicHolidayRepository holidayRepo;
    @Autowired ResourceRepository resourceRepo;

    @Test
    void contextLoads() {
        // Master data seeded from application.yml
        assertThat(holidayRepo.count()).isGreaterThanOrEqualTo(1);
        assertThat(resourceRepo.count()).isEqualTo(18);
    }

    @Test
    void holidaysSeededCorrectly() {
        assertThat(holidayRepo.findAll())
                .anyMatch(h -> "Holi".equals(h.getHolidayName()));
    }

    @Test
    void resourcesSeededCorrectly() {
        assertThat(resourceRepo.findByName("Harpreet Singh Gulati")).isPresent();
        assertThat(resourceRepo.findByName("Umesh Singh Kalakoti")).isPresent();
    }
}
