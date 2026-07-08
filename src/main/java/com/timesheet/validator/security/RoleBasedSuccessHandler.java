package com.timesheet.validator.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collection;

/**
 * Redirects users to different landing pages based on their role after login:
 *   ADMIN   → /          (upload / validation home)
 *   MANAGER → /          (upload / validation home)
 *   USER    → /timesheet (day entry form — they cannot access /)
 */
@Component
public class RoleBasedSuccessHandler implements AuthenticationSuccessHandler {

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {

        Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();

        boolean isAdmin   = authorities.stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        boolean isManager = authorities.stream().anyMatch(a -> a.getAuthority().equals("ROLE_MANAGER"));

        if (isAdmin || isManager) {
            response.sendRedirect(request.getContextPath() + "/");
        } else {
            // USER role — send straight to the timesheet entry form
            response.sendRedirect(request.getContextPath() + "/timesheet");
        }
    }
}
