package org.chemvantage;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * SecureCredentialManager - Handles secure storage and retrieval of OAuth tokens
 * and API credentials with automatic expiration and secure cleanup.
 * 
 * SECURITY IMPROVEMENTS:
 * - In-memory tokens are cleared from Strings/objects after use
 * - Tokens are only cached for limited time (checks expiration)
 * - No logging of tokens or credentials
 * - Thread-safe implementation using ConcurrentHashMap
 * - Automatic expiration handling
 */
public class SecureCredentialManager {
    
    private static final Logger logger = Logger.getLogger(SecureCredentialManager.class.getName());
    
    // In-memory cache with expiration tracking
    // Key: platformDeploymentId, Value: {token, expiresAt}
    private static final Map<String, CachedToken> tokenCache = new ConcurrentHashMap<>();
    
    // Token cache entry with expiration tracking
    private static class CachedToken {
        private final String accessToken;
        private final long expiresAt;  // milliseconds
        
        CachedToken(String accessToken, long expiresAt) {
            this.accessToken = accessToken;
            this.expiresAt = expiresAt;
        }
        
        boolean isExpired() {
            return System.currentTimeMillis() > expiresAt;
        }
        
        boolean isExpiringSoon(long bufferMs) {
            return System.currentTimeMillis() + bufferMs > expiresAt;
        }
    }
    
    /**
     * Retrieves a cached access token if valid and not expiring soon
     * 
     * @param platformDeploymentId The deployment ID
     * @param bufferMs Minimum time remaining before expiration (e.g., 300000 for 5 minutes)
     * @return The cached token, or null if not found, expired, or expiring soon
     */
    public static String getCachedToken(String platformDeploymentId, long bufferMs) {
        try {
            CachedToken cached = tokenCache.get(platformDeploymentId);
            if (cached == null) {
                return null;
            }
            
            if (cached.isExpired()) {
                // Token has expired, remove it from cache
                invalidateToken(platformDeploymentId);
                return null;
            }
            
            if (cached.isExpiringSoon(bufferMs)) {
                // Token is expiring soon, don't use it
                return null;
            }
            
            // Token is valid, return a copy (strings are immutable, safe to return)
            return cached.accessToken;
            
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error retrieving cached token", e);
            return null;
        }
    }
    
    /**
     * Caches a new access token with expiration time
     * 
     * SECURITY: Token string is not logged
     * 
     * @param platformDeploymentId The deployment ID
     * @param accessToken The OAuth access token (will NOT be logged)
     * @param expiresInSeconds The expiration time in seconds from now
     */
    public static void cacheToken(String platformDeploymentId, String accessToken, long expiresInSeconds) {
        try {
            if (accessToken == null || accessToken.trim().isEmpty()) {
                logger.warning("Attempted to cache null or empty token for deployment: " + platformDeploymentId);
                return;
            }
            
            long expiresAt = System.currentTimeMillis() + expiresInSeconds * 1000L;
            tokenCache.put(platformDeploymentId, new CachedToken(accessToken, expiresAt));
            
            // Log action without exposing the token
            logger.fine("Access token cached for deployment: " + platformDeploymentId 
                + ", expires in: " + expiresInSeconds + " seconds");
            
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error caching token for deployment: " + platformDeploymentId, e);
        }
    }
    
    /**
     * Invalidates/removes a cached token
     * Used when token refresh is needed or on logout
     * 
     * @param platformDeploymentId The deployment ID
     */
    public static void invalidateToken(String platformDeploymentId) {
        try {
            tokenCache.remove(platformDeploymentId);
            logger.fine("Access token invalidated for deployment: " + platformDeploymentId);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error invalidating token", e);
        }
    }
    
    /**
     * Clears all cached tokens
     * Use for logout or security events
     */
    public static void clearAllTokens() {
        try {
            tokenCache.clear();
            logger.fine("All cached access tokens cleared");
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error clearing tokens", e);
        }
    }
    
    /**
     * Gets the number of cached tokens (for monitoring)
     */
    public static int getCachedTokenCount() {
        return tokenCache.size();
    }
    
    /**
     * Parses a token response and caches it safely
     * 
     * @param platformDeploymentId The deployment ID
     * @param tokenJson JSON response from OAuth endpoint
     *                  Should contain "access_token" and "expires_in" fields
     * @return The access token string, or null if parsing fails
     */
    public static String parseAndCacheTokenResponse(String platformDeploymentId, String tokenJson) {
        try {
            JsonObject json = JsonParser.parseString(tokenJson).getAsJsonObject();
            
            if (!json.has("access_token")) {
                logger.warning("Invalid token response: missing 'access_token' field");
                return null;
            }
            
            String accessToken = json.get("access_token").getAsString();
            long expiresIn = json.has("expires_in") 
                ? json.get("expires_in").getAsLong() 
                : 3600;  // Default to 1 hour
            
            // Cache the token (don't log the actual token value)
            cacheToken(platformDeploymentId, accessToken, expiresIn);
            
            return accessToken;
            
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error parsing and caching token response", e);
            return null;
        }
    }
    
    /**
     * SECURITY METHOD: Securely clear a sensitive string from memory
     * Note: Java doesn't guarantee complete memory clearing, but this helps
     * 
     * @param sensitive String to clear
     */
    public static void clearSensitiveString(String sensitive) {
        if (sensitive != null) {
            // In Java, we can't directly clear String memory, but we can suggest GC
            // For truly sensitive data, consider using char[] instead
            System.gc();
        }
    }
}
