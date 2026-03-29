package com.jobpilot.jobpilot_backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * JobPilot — Automated Job Application Platform
 * @EnableScheduling needed for the job scraper scheduler (later module)
 */
@SpringBootApplication
@EnableScheduling
public class JobPilotBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(JobPilotBackendApplication.class, args);
	}

}
