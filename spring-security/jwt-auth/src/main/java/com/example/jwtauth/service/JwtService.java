package com.example.jwtauth.service;

import java.security.Key;
import java.util.Date;
import java.util.HashMap;

import org.springframework.stereotype.Service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;

@Service
public class JwtService {

    public static final String SECRET = "MCGM/5FWmvIXRixfO/fBDBZ+Un3jqIPve3niIAQ5A9B/yQFgHSg9J1RUAWnLqofn3ImQV9XnJQdF87PAYf0k/g==";

    public String generateToken(String username, String role){
        HashMap<String, Object> claims = new HashMap<>();
        claims.put("Role", role);

        return Jwts.builder()
                    .setSubject(username)
                    .setIssuedAt(new Date())
                    .setExpiration(new Date(System.currentTimeMillis() + 1000*60*30))
                    .addClaims(claims)
                    .signWith(getSignedKey(), SignatureAlgorithm.HS256)
                    .compact();
    }

    private Key getSignedKey(){
        return Keys.hmacShaKeyFor(SECRET.getBytes());
    }

    public Claims verifySignatureAndExtractClaims(String token){
        return Jwts.parser()
            .setSigningKey(getSignedKey())
            .build()
            .parseClaimsJws(token)
            .getBody();
    }

    public String extractUserName(String token){
        return verifySignatureAndExtractClaims(token).getSubject();
    }

    public Date getExpriration(String token){
        return verifySignatureAndExtractClaims(token).getExpiration();
    }

    public boolean isExpired(String token){
        return getExpriration(token).before(new Date());
    }
}
