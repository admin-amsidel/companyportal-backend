/*
 * Copyright (c) 2026 IANEL Technologies. All rights reserved.
 * Designed and developed by IANEL Technologies.
 */
package com.companyportal.backend.security;

import com.companyportal.backend.entity.Employee;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/** Issues the signed access tokens (JWT, HS256) that the web app sends with every API call. */
@Service
public class TokenService {

    private final JwtEncoder jwtEncoder;
    private final com.companyportal.backend.service.SessionSettings sessions;
    private final String issuer;

    public TokenService(JwtEncoder jwtEncoder,
                        com.companyportal.backend.service.SessionSettings sessions,
                        @Value("${app.jwt.issuer}") String issuer) {
        this.jwtEncoder = jwtEncoder;
        this.sessions = sessions;
        this.issuer = issuer;
    }

    public IssuedToken issue(Employee employee) {
        // whole seconds, matching the precision of the "iat" claim (see EmployeeTokenConverter)
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        Instant expiresAt = now.plus(sessions.maxValidity());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(String.valueOf(employee.getId()))
                .issuedAt(now)
                .expiresAt(expiresAt)
                .claim("userId", employee.getUserId())
                .claim("role", employee.getRole().name())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new IssuedToken(token, expiresAt);
    }

    public record IssuedToken(String token, Instant expiresAt) {
    }
}
