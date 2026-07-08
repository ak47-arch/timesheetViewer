package com.timesheet.validator.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final AppUserDetailsService    userDetailsService;
    private final RoleBasedSuccessHandler  successHandler;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public DaoAuthenticationProvider authProvider() {
        var p = new DaoAuthenticationProvider();
        p.setUserDetailsService(userDetailsService);
        p.setPasswordEncoder(passwordEncoder());
        return p;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authenticationProvider(authProvider())
            .authorizeRequests(auth -> auth
                // ── Public ───────────────────────────────────────────────────
                .antMatchers("/css/**", "/js/**", "/images/**", "/favicon.ico").permitAll()
                .antMatchers("/login").permitAll()
                .antMatchers("/403").permitAll()

                // ── ADMIN only ────────────────────────────────────────────────
                .antMatchers("/h2-console/**").hasRole("ADMIN")
                .antMatchers("/admin/**").hasRole("ADMIN")

                // ── MANAGER + ADMIN ───────────────────────────────────────────
                .antMatchers("/", "/upload", "/view/**", "/validate/**")
                    .hasAnyRole("MANAGER", "ADMIN")
                // Lazy per-tab sheet JSON — same audience as the viewer pages
                .antMatchers("/api/view/**")
                    .hasAnyRole("MANAGER", "ADMIN")
                .antMatchers("/export-csv/**")
                    .hasAnyRole("MANAGER", "ADMIN")

                // ── All authenticated users ────────────────────────────────────
                .antMatchers("/timesheet", "/timesheet/**")
                    .hasAnyRole("USER", "MANAGER", "ADMIN")

                // ── Anything else needs authentication ─────────────────────────
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/login")
                // Role-aware redirect — USER → /timesheet, others → /
                .successHandler(successHandler)
                .failureUrl("/login?error")
                .permitAll()
            )
            .logout(logout -> logout
                .logoutRequestMatcher(new AntPathRequestMatcher("/logout"))
                .logoutSuccessUrl("/login?logout")
                .invalidateHttpSession(true)
                .clearAuthentication(true)
                .permitAll()
            )
            // Custom 403 page instead of whitelabel
            .exceptionHandling(ex -> ex
                .accessDeniedPage("/403")
            )
            .headers(h -> h.frameOptions().sameOrigin())
            .csrf(csrf -> csrf.ignoringAntMatchers("/h2-console/**"));

        return http.build();
    }
}
