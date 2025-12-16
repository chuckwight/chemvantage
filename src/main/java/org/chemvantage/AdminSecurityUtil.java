package org.chemvantage;

import java.util.logging.Level;
import java.util.logging.Logger;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Security utility for admin-only endpoints
 * Provides authentication checks and audit logging
 */
public class AdminSecurityUtil {
    
    private static final Logger logger = Logger.getLogger(AdminSecurityUtil.class.getName());
    
    /**
     * Verify the request is from an authenticated admin user or service account
     * @return true if user is authenticated admin, false otherwise
     */
    public static boolean isAdminAuthenticated(HttpServletRequest request) {
        try {
            com.google.appengine.api.users.UserService userService = 
                com.google.appengine.api.users.UserServiceFactory.getUserService();
            
            com.google.appengine.api.users.User appEngineUser = userService.getCurrentUser();
            
            if (appEngineUser == null) {
                return false;
            }
            
            return userService.isUserAdmin();
            
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error during admin authentication check", e);
            return false;
        }
    }
    
    /**
     * Handle authentication failure with proper response and logging
     */
    public static void handleAuthenticationFailure(
            HttpServletRequest request, 
            HttpServletResponse response,
            String endpoint) throws Exception {
        
        logSecurityEvent("UNAUTHORIZED_ADMIN_ACCESS", endpoint, request);
        response.sendError(HttpServletResponse.SC_FORBIDDEN, 
            "Admin authentication required");
    }
    
    /**
     * Log security events for audit trail
     */
    public static void logSecurityEvent(String eventType, String endpoint, HttpServletRequest request) {
        try {
            String clientIp = getClientIp(request);
            String userAgent = request.getHeader("User-Agent");
            String timestamp = String.valueOf(System.currentTimeMillis());
            
            logger.warning(String.format(
                "SECURITY_EVENT | Type: %s | Endpoint: %s | IP: %s | UserAgent: %s | Time: %s",
                eventType,
                endpoint,
                clientIp,
                userAgent != null ? userAgent.substring(0, Math.min(50, userAgent.length())) : "unknown",
                timestamp
            ));
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error logging security event", e);
        }
    }
    
    /**
     * Extract real client IP, accounting for proxies
     */
    private static String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // Return only the first IP if multiple are present
        return ip.split(",")[0].trim();
    }
}
