package com.example.activity.config;

import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.config.Customizer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.security.SecurityProperties;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;

@Configuration
@ConditionalOnProperty(name = "activity.auth.mode", havingValue = "local")
public class SecurityConfig {
    @Bean
    public UserDetailsService localUsers(SecurityProperties properties) {
        String password = properties.getUser().getPassword();
        if (password == null || password.isBlank()) throw new IllegalArgumentException("API_PASSWORD is required for local authentication");
        return new InMemoryUserDetailsManager(User.withUsername(properties.getUser().getName())
            .password(PasswordEncoderFactories.createDelegatingPasswordEncoder().encode(password)).roles("USER").build());
    }
    @Bean
    public SecurityFilterChain security(HttpSecurity http) throws Exception {
        return http.authorizeHttpRequests(auth -> auth.requestMatchers("/actuator/health", "/oauth/*/callback").permitAll()
            .anyRequest().authenticated()).httpBasic(Customizer.withDefaults()).build();
    }
}
