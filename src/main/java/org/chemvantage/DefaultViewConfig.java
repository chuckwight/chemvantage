package org.chemvantage;

import org.springframework.core.Ordered;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class DefaultViewConfig implements WebMvcConfigurer {
	
	// This method tells the DefaultServlet to forward "/" requests to 
	// to the welcome servlet bound to the request "/home"
	// Do not bind the servlet directly to "/" because the DefaultServlet
	// is also responsible for serving static files.
    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/").setViewName("/index.html");
        registry.setOrder(Ordered.HIGHEST_PRECEDENCE);
    }
}