package com.example.activity.api;

import org.springframework.web.bind.annotation.*;
import java.security.Principal;
import java.util.Map;

@RestController
public class UserController {
    @GetMapping("/api/me")
    public Map<String, String> me(Principal principal) { return Map.of("userId", principal.getName()); }
}
