package com.example.activity.api;

import com.example.activity.service.OAuthService;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import java.security.Principal;
import java.util.Map;

@RestController
public class OAuthController {
    private final OAuthService oauth;
    public OAuthController(OAuthService oauth) { this.oauth = oauth; }
    @GetMapping("/oauth/{provider}/authorize")
    public ResponseEntity<Void> authorize(@PathVariable String provider, Principal principal, HttpSession session) {
        var authorization = oauth.authorize(provider, principal.getName());
        session.setAttribute("oauth:" + provider, authorization.pending());
        return ResponseEntity.status(HttpStatus.FOUND).location(authorization.url()).build();
    }
    @GetMapping("/oauth/{provider}/callback")
    public Map<String, String> callback(@PathVariable String provider, @RequestParam(required = false) String state,
                                      @RequestParam(required = false) String code, HttpSession session) {
        OAuthService.Pending pending;
        synchronized (session) {
            pending = (OAuthService.Pending) session.getAttribute("oauth:" + provider);
            session.removeAttribute("oauth:" + provider);
        }
        oauth.complete(provider, state, code, pending);
        return Map.of("provider", provider, "status", "connected");
    }
    @PostMapping("/oauth/garmin/refresh")
    public Map<String, String> refresh(Principal principal) { oauth.refreshGarmin(principal.getName()); return Map.of("status", "refreshed"); }
}
