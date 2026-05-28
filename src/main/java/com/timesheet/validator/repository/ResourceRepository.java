package com.timesheet.validator.repository;
import com.timesheet.validator.domain.Resource;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface ResourceRepository extends JpaRepository<Resource,Long> {
    Optional<Resource> findByName(String name);
}
