package com.riseai.security;

import com.riseai.user.UserRepository;
import com.riseai.user.UserStatus;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private static final String BEARER = "Bearer ";

    private final JwtService jwtService;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(BEARER) && SecurityContextHolder.getContext().getAuthentication() == null) {
            jwtService.verifyAccessToken(header.substring(BEARER.length()).trim())
                    .flatMap(userRepository::findById)
                    .filter(user -> user.getStatus() == UserStatus.ACTIVE)
                    .ifPresent(user -> {
                        AuthUser principal = new AuthUser(user.getId(), user.getEmail(), user.getRoles());
                        SecurityContext context = SecurityContextHolder.createEmptyContext();
                        context.setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, principal.authorities()));
                        SecurityContextHolder.setContext(context);
                    });
        }
        chain.doFilter(request, response);
    }
}
