package org.chemvantage;

import org.springframework.stereotype.Component;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

@Component // Registers this filter globally in Spring Boot
public class DomainRedirectFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) 
        throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse res = (HttpServletResponse) response;
    
        String serverName = req.getServerName();
    
        // Check for naked domain
        if ("chemvantage.org".equalsIgnoreCase(serverName)) {
            String path = req.getRequestURI();
            String query = req.getQueryString() != null ? "?" + req.getQueryString() : "";
            String destination = "https://www.chemvantage.org" + path + query;
        
            // Use 308 (Permanent Redirect) or 307 (Temporary) to preserve POST methods for LTI
            res.setStatus(308); 
            res.setHeader("Location", destination);
            res.flushBuffer(); // Force the response out
            return; 
        }
    
        chain.doFilter(request, response);
    }
}
