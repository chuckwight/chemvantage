package org.chemvantage;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.web.servlet.ServletComponentScan;

@ServletComponentScan
@SpringBootApplication(exclude = { SecurityAutoConfiguration.class })
public class SpringbootMain {

	
	// This class scans the classes for annotated 
	// servlets and starts the embedded tomcat server
	public static void main(String[] args) {
		SpringApplication.run(SpringbootMain.class, args);
	}
	
}

