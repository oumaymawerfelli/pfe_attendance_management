package com.example.pfe.Service;

import org.springframework.stereotype.Service;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TokenBlacklistService {
    private final Set<String> blacklistedTokens = ConcurrentHashMap.newKeySet();

    public void blacklist(String token) {
        blacklistedTokens.add(token);
        System.out.println("Token blacklisté: " + token.substring(0, 10) + "...");
    }

    public boolean isBlacklisted(String token) {
        return blacklistedTokens.contains(token);
    }
}