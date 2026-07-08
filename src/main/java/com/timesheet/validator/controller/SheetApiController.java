package com.timesheet.validator.controller;

import com.timesheet.validator.dto.SheetDto;
import com.timesheet.validator.service.SheetViewService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * JSON API backing the viewer's lazy, per-tab sheet loading.
 *
 * <p>The viewer page now ships only the tab bar + issues panel. Each sheet's
 * grid is fetched on demand from {@code /api/view/{sessionId}/sheet/{index}},
 * which keeps the initial page small instead of serialising every sheet
 * (~8&nbsp;MB) into one response.</p>
 *
 * <p>Authorisation mirrors the {@code /view/**} pages (MANAGER + ADMIN) via
 * the {@code /api/view/**} matcher in {@code SecurityConfig}.</p>
 */
@RestController
@RequestMapping("/api/view")
@RequiredArgsConstructor
@Slf4j
public class SheetApiController {

    private final SheetViewService sheetView;

    @GetMapping("/{sessionId}/sheet/{index}")
    public ResponseEntity<SheetDto> sheet(@PathVariable String sessionId,
                                          @PathVariable int index) {
        log.debug("[SheetApi] Lazy load session={} sheet={}", sessionId, index);
        SheetDto dto = sheetView.getSheet(sessionId, index);
        return ResponseEntity.ok(dto);
    }
}
