package com.timesheet.validator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableConfigurationProperties
@EnableScheduling
public class TimesheetViewerApplication {
    public static void main(String[] args) {

        SpringApplication.run(TimesheetViewerApplication.class, args);
        System.out.println("Application is running");
    }
}
