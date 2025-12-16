# ChemVantage Security Analysis Report

**Date:** December 16, 2025  
**Project:** ChemVantage (Java/Jakarta Servlet Application)

## Executive Summary

This report identifies **7 critical/high-severity security issues** and **3 medium-severity issues** found during code review of the ChemVantage Java codebase. The application handles user authentication, educational content, and payment processing, making security particularly important.

---

## Critical Issues

### 1. **Weak Cryptographic Encoding for User Tokens** ⚠️ CRITICAL
**File:** [User.java](User.java#L109-L270)  
**Issue:** The application uses a custom, weak encryption scheme for token/ID encoding

```java
// User.java lines 109-264
static long encode(long encrypt) {
    /* Weak encoding of the expiration Date takes place in 3 steps using 
     * 4 groups of 3 hexdigits... */
    // Custom XOR-based encryption with weak mixing
}

static String encryptId(String id, long sig) {
    /* This method uses a simple one-time pad to encrypt a userId value...
     * The final output should have 2 characters for every byte of input, 
     * so the encryption is weak. */
```

**Risk:** 
- Reliance on custom, unvalidated cryptography
- One-time pad is only secure if the keystream is truly random and never reused
- Using `Random` class which is cryptographically insecure
- Developers themselves admit the encryption is "weak"

**Recommendation:**
- Use `java.security.KeyPairGenerator` with established algorithms (RSA-2048 already used in RSAKeyPair.java)
- Switch to standard JWT/JWS libraries (e.g., auth0/java-jwt or spring-security)
- Use `SecureRandom` instead of `Random`
- Have cryptographic implementations reviewed by security experts

---

### 2. **Potential URL Parameter Injection / XSS in Token Signatures** ⚠️ CRITICAL
**File:** [Sage.java](Sage.java#L52), [Sage.java](Sage.java#L178), [Sage.java](Sage.java#L215)  
**Issue:** User token signatures are embedded directly in URLs and HTML without encoding

```java
// Sage.java line 215
+ "<input type=hidden name=sig value=" + user.getTokenSignature() + " />"

// Sage.java line 178
+ " setTimeout(() => { window.location.replace('/Sage?sig=" 
+ user.getTokenSignature() + "&ConceptId=" + (concept != null ? concept.id : 0) + "'); }, 2000);"

// Sage.java line 622
buf.append("<li><a href=/Sage?ConceptId=" + cId + "&sig=" 
+ user.getTokenSignature() + (st.scores[st.conceptIds.indexOf(cId)]==0?"&UserRequest=ConceptDescription":"") + ">"
```

**Risk:**
- If `user.getTokenSignature()` contains special characters, it could break out of attributes
- No HTML escaping applied before inserting into HTML/JavaScript context
- Could enable XSS attacks if tokens are ever tampered with
- No URL encoding for parameter values

**Recommendation:**
- Use proper HTML encoding utilities: `com.google.common.html.HtmlEscapers` or `org.apache.commons.text.StringEscapeUtils`
- Use URL encoding for query parameters: `URLEncoder.encode(value, StandardCharsets.UTF_8)` (already imported in Sage.java line 14)
- Apply escaping at the point of output, not input

```java
// Better:
+ "<input type=hidden name=sig value=\"" 
+ HtmlEscapers.htmlEscaper().escape(user.getTokenSignature()) + "\" />"

// For URLs:
+ "/Sage?sig=" + URLEncoder.encode(user.getTokenSignature(), StandardCharsets.UTF_8)
```

---

### 3. **Insecure Token/Session Storage in Client-Side Session Storage** ⚠️ CRITICAL
**File:** [Token.java](Token.java#L97), [LTIv1p3Launch.java](LTIv1p3Launch.java#L1013)  
**Issue:** Sensitive token data stored in browser sessionStorage

```javascript
// Token.java line 97
window.sessionStorage.setItem('sig','" + nonceHash + "');

// LTIv1p3Launch.java line 1013-1014
let sig = parseInt(" + shortSig + ",10) + parseInt(window.sessionStorage.getItem('sig'),10);
window.sessionStorage.clear();
```

**Risk:**
- `sessionStorage` is vulnerable to XSS attacks (if XSS exists, attacker can read sessionStorage)
- Session tokens in sessionStorage can be accessed by any script on the page
- No HttpOnly flag is used (can't use sessionStorage with HttpOnly, but indicates design issue)
- Exposing numeric signatures that appear to be combined arithmetically

**Recommendation:**
- Store authentication tokens in HttpOnly, Secure cookies (set by server only)
- Never store sensitive data in sessionStorage/localStorage
- Use server-side session management instead
- Implement CSRF tokens properly for state-changing operations

```java
// Set cookie in server response:
Cookie cookie = new Cookie("sig", tokenValue);
cookie.setHttpOnly(true);
cookie.setSecure(true);  // HTTPS only
cookie.setPath("/");
response.addCookie(cookie);

// Remove sessionStorage usage in JavaScript
```

---

### 4. **Insufficient Input Validation on User Parameters** ⚠️ CRITICAL
**File:** [Sage.java](Sage.java#L62-L63), [Sage.java](Sage.java#L90)  
**Issue:** Parameters parsed with minimal validation

```java
// Sage.java lines 62-63
long questionId = Long.parseLong(request.getParameter("QuestionId"));
long parameter = Long.parseLong(request.getParameter("Parameter"));

// Sage.java line 90
conceptId = Long.parseLong(request.getParameter("ConceptId"));
```

**Risk:**
- `Long.parseLong()` can throw `NumberFormatException` if input is invalid
- No validation that IDs belong to the current user's context
- Could allow unauthorized access to other users' questions/concepts
- No rate limiting to prevent enumeration attacks

**Recommendation:**
- Wrap in try-catch blocks with proper error handling
- Validate that fetched entities belong to the authenticated user
- Implement authorization checks after retrieval
- Add request rate limiting per user

```java
try {
    long questionId = Long.parseLong(request.getParameter("QuestionId"));
    if (questionId <= 0) throw new IllegalArgumentException("Invalid question ID");
    
    // Verify user has access to this question
    Question q = ofy().load().key(Key.create(Question.class, questionId)).safe();
    if (!userCanAccess(user, q)) {
        response.sendError(HttpServletResponse.SC_FORBIDDEN);
        return;
    }
    // ... proceed
} catch (NumberFormatException e) {
    response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid question ID format");
}
```

---

### 5. **Unencrypted API Credentials in Code/Configuration** ⚠️ CRITICAL
**File:** Multiple OAuth/Payment Processing Files  
**Issue:** OAuth tokens and API credentials may be hardcoded or insufficiently protected

```java
// LTIMessage.java line 51
static Map<String,String> authTokens = new HashMap<String,String>();

// Checkout.java line 219
String body = "grant_type=client_credentials";
```

**Risk:**
- OAuth access tokens cached in static HashMap without encryption
- Client credentials transmitted without verification of HTTPS
- No token rotation or expiration handling visible
- Credentials could be exposed in logs or memory dumps

**Recommendation:**
- Store credentials in secure key management system (Google Cloud Secret Manager, AWS Secrets Manager)
- Use environment variables for deployment-specific secrets
- Implement token refresh mechanism with proper expiration
- Never log credentials or tokens
- Use HTTPS for all credential transmission

```java
// Instead of static HashMap:
import com.google.cloud.secretmanager.v1.*;

String getOAuthToken(String platformId) throws Exception {
    SecretManagerServiceClient client = SecretManagerServiceClient.create();
    SecretVersionName secretVersionName = 
        SecretVersionName.of(projectId, "oauth-token-" + platformId, "latest");
    AccessSecretVersionResponse response = client.accessSecretVersion(secretVersionName);
    return response.getPayload().getData().toStringUtf8();
}
```

---

### 6. **Missing Authentication on Administrative Endpoints** ⚠️ CRITICAL
**File:** [Edit.java](Edit.java#L43), [EraseEntity.java](EraseEntity.java#L41)  
**Issue:** Admin endpoints rely only on App Engine built-in auth

```java
// Both files reference:
// "by specifying login: admin in a url handler of the project app.yaml file"
```

**Risk:**
- Relies entirely on declarative security (app.yaml) rather than runtime checks
- No programmatic enforcement of admin role
- Difficult to audit and debug access control
- May bypass if configuration is not properly deployed
- No audit logging of admin actions

**Recommendation:**
- Implement runtime authentication checks in code (already done in AdminSecurityUtil.java)
- Always verify admin status in servlets before executing sensitive operations
- Apply to ALL admin endpoints
- Add comprehensive audit logging

```java
// At start of doGet/doPost in admin servlets:
if (!AdminSecurityUtil.isAdminAuthenticated(request)) {
    AdminSecurityUtil.handleAuthenticationFailure(request, response, "/Edit");
    return;
}

// Log the action
AdminSecurityUtil.logSecurityEvent("ADMIN_ACTION", "EditQuestion_" + questionId, request);
```

---

### 7. **Weak Random Number Generation for Nonce/Security Tokens** ⚠️ CRITICAL
**File:** [User.java](User.java#L109), [Nonce.java](Nonce.java)  
**Issue:** Using `Random` class instead of `SecureRandom` for security-sensitive operations

```java
// User.java line 276
Random rand = new Random(sig);  // NOT SECURE
for (int i=0;i<input.length;i++) {
    int a = rand.nextInt(128);  // Predictable
    int b = (int) input[i];
    int xor = a^b;
    output += (xor<16?"0":"") + Integer.toHexString(a^b);
}
```

**Risk:**
- `java.util.Random` is predictable and not cryptographically secure
- Can be seeded and replayed
- Attackers can predict future tokens if they know the seed
- Standard library provides `SecureRandom` for exactly this purpose

**Recommendation:**
- Replace all `new Random()` with `new SecureRandom()` for security operations
- Use `java.security.SecureRandom` throughout the codebase

```java
private static final SecureRandom secureRandom = new SecureRandom();

static String encryptId(String id, long sig) {
    try {
        byte[] input = id.getBytes("UTF-8");
        String output = "";
        // Use SecureRandom instead of new Random(sig)
        byte[] randomBytes = new byte[input.length];
        secureRandom.nextBytes(randomBytes);
        
        for (int i = 0; i < input.length; i++) {
            int a = randomBytes[i] & 0xFF;
            int b = (int) input[i];
            int xor = a ^ b;
            output += (xor < 16 ? "0" : "") + Integer.toHexString(xor);
        }
        return output;
    } catch (Exception e) {
        return null;
    }
}
```

---

## High-Severity Issues

### 8. **No CSRF Token Protection on State-Changing Operations** ⚠️ HIGH
**File:** [LTIv1p3Launch.java](LTIv1p3Launch.java#L376)  
**Issue:** State-changing operations lack proper CSRF protection

```java
// LTIv1p3Launch.java line 376
// Create a cross-site request forgery (CSRF) token containing the Assignment.id
```

**Risk:**
- Comment indicates CSRF token awareness, but unclear if universally implemented
- POST forms in HTML lack CSRF tokens in most files reviewed
- No verification of origin/referer headers
- Vulnerable to clickjacking and CSRF attacks

**Recommendation:**
- Generate unique CSRF tokens per session
- Include token in all state-changing forms
- Validate token on processing
- Use Spring Security CSRF protection or similar library

---

### 9. **Insufficient Logging and Monitoring** ⚠️ HIGH
**File:** Most servlet files  
**Issue:** Limited security event logging

**Risk:**
- Difficult to detect and investigate security breaches
- No audit trail for sensitive operations
- Compliance issues (FERPA, GDPR may require audit logs)

**Recommendation:**
- Log all authentication attempts
- Log all authorization failures
- Log data access for sensitive academic information
- Use structured logging (JSON format)
- Send logs to centralized log management

---

### 10. **Unvalidated Redirects in URLs** ⚠️ HIGH
**File:** [Sage.java](Sage.java#L178)  
**Issue:** Redirect destinations may be user-controllable

```java
// Example of redirect pattern in codebase:
window.location.replace('/Sage?sig=" + user.getTokenSignature() + ...
```

**Risk:**
- Could redirect users to malicious sites
- Open redirect vulnerability

**Recommendation:**
- Validate all redirect destinations
- Use whitelist of allowed URLs
- Never include user input in redirects

---

## Medium-Severity Issues

### 11. **Missing Security Headers** ⚠️ MEDIUM
**Issue:** No indication of security headers in response handling

**Risk:**
- No Content Security Policy (CSP) prevents inline script execution
- No X-Frame-Options prevents clickjacking
- No X-Content-Type-Options prevents MIME sniffing

**Recommendation:**
Add security headers in all responses:
```java
response.setHeader("X-Frame-Options", "DENY");
response.setHeader("X-Content-Type-Options", "nosniff");
response.setHeader("Content-Security-Policy", "default-src 'self'");
response.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
response.setHeader("X-XSS-Protection", "1; mode=block");
```

---

### 12. **Exception Messages May Leak Information** ⚠️ MEDIUM
**File:** [Sage.java](Sage.java#L403)  
**Issue:** Exception messages displayed to users

```java
// Sage.java line 403
buf.append("<p>" + e.getMessage()==null?e.toString():e.getMessage());
```

**Risk:**
- Stack traces reveal internal implementation details
- Attackers learn system architecture
- Supports reconnaissance

**Recommendation:**
- Log full exception details on server-side only
- Display generic messages to users
- Never display stack traces to users

```java
logger.severe("Error in Sage servlet: " + e);
buf.append("<p>An unexpected error occurred. Please contact support with reference ID: " 
    + generateErrorId() + "</p>");
```

---

### 13. **No Rate Limiting** ⚠️ MEDIUM
**Issue:** No apparent rate limiting on user submissions

**Risk:**
- Vulnerable to brute force attacks
- Denial of service (quiz spam)
- Resource exhaustion

**Recommendation:**
- Implement rate limiting per user IP/session
- Use libraries like Bucket4j or Guava RateLimiter
- Limit login attempts, question submissions, etc.

---

## Summary Table

| Issue | Severity | File | Category |
|-------|----------|------|----------|
| Weak Cryptography | CRITICAL | User.java | Cryptography |
| URL/XSS in Tokens | CRITICAL | Sage.java | XSS/Injection |
| Client-Side Token Storage | CRITICAL | Token.java | Session Management |
| Input Validation | CRITICAL | Sage.java | Input Validation |
| Unencrypted Credentials | CRITICAL | Multiple | Credentials |
| Missing Auth on Admin Endpoints | CRITICAL | Edit.java, EraseEntity.java | Authentication |
| Weak RNG | CRITICAL | User.java | Cryptography |
| Missing CSRF | HIGH | LTIv1p3Launch.java | CSRF |
| Insufficient Logging | HIGH | Multiple | Monitoring |
| Unvalidated Redirects | HIGH | Multiple | Redirect |
| Missing Security Headers | MEDIUM | Multiple | HTTP Headers |
| Exception Leakage | MEDIUM | Sage.java | Information Disclosure |
| No Rate Limiting | MEDIUM | Multiple | DoS Prevention |

---

## Next Steps

1. **Immediate (Week 1):**
   - Replace weak cryptography with standard JWT/JWS
   - Move tokens to HttpOnly cookies
   - Add HTML/URL encoding to all user-facing output
   - Implement authentication checks on admin endpoints

2. **Short-term (Week 2-3):**
   - Implement CSRF token protection
   - Add security headers to all responses
   - Implement rate limiting
   - Add comprehensive security logging

3. **Long-term (Month 1+):**
   - Complete security audit by external firm
   - Implement automated security testing (SAST/DAST)
   - Deploy Web Application Firewall (WAF)
   - Regular penetration testing

---

## References

- OWASP Top 10: https://owasp.org/Top10/
- CWE List: https://cwe.mitre.org/
- Java Security Best Practices: https://cheatsheetseries.owasp.org/cheatsheets/Java_Security_Cheat_Sheet.html
- Jakarta EE Security: https://eclipse-ee4j.github.io/security/

