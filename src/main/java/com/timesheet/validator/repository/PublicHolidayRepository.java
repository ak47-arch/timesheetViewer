package com.timesheet.validator.repository;
import com.timesheet.validator.domain.PublicHoliday;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate; import java.util.List;
public interface PublicHolidayRepository extends JpaRepository<PublicHoliday,Long> {
    boolean existsByHolidayDate(LocalDate date);
    List<PublicHoliday> findByCountryCode(String countryCode);
}
