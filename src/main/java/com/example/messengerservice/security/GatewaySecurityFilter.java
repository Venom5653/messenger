package com.example.messengerservice.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class GatewaySecurityFilter extends OncePerRequestFilter {

    @Value("${gateway.internal-secret}")
    private String gatewaySecret;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {

        String secret = request.getHeader("X-Gateway-Secret");

        if (!gatewaySecret.equals(secret)) {

            response.setStatus(HttpServletResponse.SC_FORBIDDEN);

            return;
        }

        filterChain.doFilter(request, response);
    }
}