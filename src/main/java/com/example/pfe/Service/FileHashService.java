package com.example.pfe.Service;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;

@Service
@Slf4j
public class FileHashService {

    // Calculer le hash SHA-256 d'un fichier
    public String calculateHash(InputStream inputStream) throws IOException {
        String hash = DigestUtils.sha256Hex(inputStream);
        log.info("File hash calculated: {}", hash);
        return hash;
    }
}