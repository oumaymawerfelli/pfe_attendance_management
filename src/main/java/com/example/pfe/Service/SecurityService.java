// src/main/java/com/example/pfe/Service/SecurityService.java
package com.example.pfe.Service;

import org.springframework.stereotype.Service;

@Service("securityService")
public class SecurityService {
    public boolean isProjectManager(Long projectId) {
        return false;
    }
}