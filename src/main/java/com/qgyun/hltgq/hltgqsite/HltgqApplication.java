package com.qgyun.hltgq.hltgqsite;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class HltgqApplication {

    public static void main(String[] args) {
        SpringApplication.run(HltgqApplication.class, args);
    }

}
