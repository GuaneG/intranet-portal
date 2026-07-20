package com.jforce.backend.config;

import com.jforce.backend.service.JWTService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JWTFilter extends OncePerRequestFilter {
    private final JWTService jwtService;

    public JWTFilter(JWTService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        Claims claims;
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            filterChain.doFilter(request,response);
            return;
        }
        String tokenWithoutBearer = header.substring(7);
        try {
         claims = jwtService.parseToken(tokenWithoutBearer);
        }catch (JwtException e) {
            filterChain.doFilter(request,response);
            return;
        }
        String sub = claims.getSubject();
        String rol = claims.get("rol",String.class);

        //bir yetki etiketi taşıyan basit bir nesnedir
        SimpleGrantedAuthority authority = new SimpleGrantedAuthority("ROLE_" + rol);
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(sub,null, List.of(authority));

        SecurityContextHolder.getContext().setAuthentication(authentication);
        filterChain.doFilter(request,response);
    }
}
