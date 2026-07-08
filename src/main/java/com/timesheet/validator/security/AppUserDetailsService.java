package com.timesheet.validator.security;
import com.timesheet.validator.domain.AppUser;
import com.timesheet.validator.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;
import java.util.stream.Collectors;
@Service @RequiredArgsConstructor
public class AppUserDetailsService implements UserDetailsService {
    private final AppUserRepository userRepo;
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        AppUser user = userRepo.findByUsername(username)
            .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
        var authorities = user.getRoles().stream()
            .map(r -> new SimpleGrantedAuthority("ROLE_" + r.getName()))
            .collect(Collectors.toList());
        return new org.springframework.security.core.userdetails.User(
            user.getUsername(), user.getPassword(),
            Boolean.TRUE.equals(user.getEnabled()), true, true, true, authorities);
    }
}
