package com.timesheet.validator.repository;

import com.timesheet.validator.domain.ResourceSow;
import com.timesheet.validator.domain.ResourceSowId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface ResourceSowRepository extends JpaRepository<ResourceSow, ResourceSowId> {
    /** All SOW numbers mapped to a given resource */
    List<ResourceSow> findByResourceId(String resourceId);

    /** All resource IDs mapped to a given SOW */
    List<ResourceSow> findBySowNumber(String sowNumber);

    /** Check if a resource is allowed to log against a SOW */
    boolean existsByResourceIdAndSowNumber(String resourceId, String sowNumber);

    @Query("SELECT rs.sowNumber FROM ResourceSow rs WHERE rs.resourceId = :rid")
    List<String> findSowNumbersByResourceId(@Param("rid") String resourceId);
}
