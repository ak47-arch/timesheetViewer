package com.timesheet.validator.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(Exception.class)
    public String handleException(Exception ex, Model model) {
        log.error("Unhandled exception", ex);
        model.addAttribute("error", "An error occurred: " + ex.getMessage());
        model.addAttribute("sessions", java.util.List.of());
        model.addAttribute("holidays", java.util.List.of());
        model.addAttribute("holidayCount", 0);
        model.addAttribute("resourceCount", 0);
        return "pages/home";
    }
}
