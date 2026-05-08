package com.fitness.activityservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ActivityserviceApplication {

	public static void main(String[] args) {
		SpringApplication.run(ActivityserviceApplication.class, args);
	}

}
