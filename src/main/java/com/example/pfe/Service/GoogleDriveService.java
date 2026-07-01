package com.example.pfe.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class GoogleDriveService {

    @Value("${google.drive.folder-id}")
    private String folderId;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    private String accessToken;
    private long tokenExpiry = 0;

    // ── Obtenir un token d'accès valide ──────────────────────────────────
    private synchronized String getAccessToken() throws IOException {
        // Si token valide encore, réutiliser
        if (accessToken != null && System.currentTimeMillis() < tokenExpiry) {
            return accessToken;
        }

        // Sinon, générer un nouveau token
        ClassPathResource resource = new ClassPathResource("arabsoft-rag-service.json");

        // Utiliser GoogleCredentials au lieu de ServiceAccountCredentials
        com.google.auth.oauth2.GoogleCredentials credentials =
                com.google.auth.oauth2.GoogleCredentials
                        .fromStream(resource.getInputStream())
                        .createScoped(List.of("https://www.googleapis.com/auth/drive"));

        // Refresh et récupérer le token
        credentials.refresh();
        accessToken = credentials.getAccessToken().getTokenValue();
        tokenExpiry = System.currentTimeMillis() + 3500 * 1000; // 58 minutes

        log.info("New Google Drive token obtained, valid until {}", tokenExpiry);
        return accessToken;
    }
// Pourquoi synchronized? Pour éviter que 2 threads demandent un token en même temps.
//Pourquoi cacher le token? Demander un nouveau token à chaque requête est lent. On en réutilise un valide.

    // ── Télécharger le fichier depuis Google Drive ────────────────────────
    //Rôle: Télécharger le contenu binaire (bytes) du fichier depuis Drive.
    //Pourquoi retourner InputStream? Pour pouvoir le passer directement à PDFBox sans écrire le fichier sur disque.
    public InputStream downloadFileFromDrive(String fileName) throws IOException {
        log.info("Searching for file: {}", fileName);

        try {
            // 1. Étape 1: Chercher l'ID du fichier
            String fileId = searchFileInFolder(fileName);
            if (fileId == null) {
                throw new FileNotFoundException("File not found in Google Drive: " + fileName);
            }

            log.info("Found file: {} (ID: {})", fileName, fileId);
            // 2. Étape 2: Construire l'URL de téléchargement
            String downloadUrl = "https://www.googleapis.com/drive/v3/files/" + fileId + "?alt=media";
            // 3. Authentification
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(getAccessToken());
            // 4. Télécharger les bytes
            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<byte[]> response = restTemplate.exchange(
                    downloadUrl, HttpMethod.GET, entity, byte[].class);  // ← FIX: utilise exchange avec headers

            if (response.getBody() == null || response.getBody().length == 0) {
                throw new IOException("Downloaded file is empty");
            }

            log.info("File downloaded successfully: {} bytes", response.getBody().length);
            // 5. Convertir bytes → InputStream (pour pouvoir le streamer)
            return new ByteArrayInputStream(response.getBody());

        } catch (FileNotFoundException e) {
            throw e;  // Re-throw pour gestion correcte
        } catch (Exception e) {
            log.error(" Erreur dans downloadFileFromDrive: {}", e.getMessage(), e);
            throw new IOException("Failed to download file: " + e.getMessage(), e);
        }
    }

    // ── Chercher un fichier par nom dans le dossier ──────────────────────
    public String searchFileInFolder(String fileName) throws IOException {
        String token = getAccessToken();
        // 1. Construire la requête Google Drive (langage Q de l'API)
        // Exemple: "'1tLImnd0...' in parents and name='reglementation.pdf' and trashed=false"
        String query = String.format("'%s' in parents and name='%s' and trashed=false",
                folderId, fileName);

        log.info(" Query Google Drive: {}", query);
        log.info(" Folder ID: {}", folderId);
        // 2. Authentification Bearer
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
// 3. Construire l'URL avec encodage CORRECT
        //  Use UriComponentsBuilder - prevents double encoding
        java.net.URI uri = org.springframework.web.util.UriComponentsBuilder
                .fromHttpUrl("https://www.googleapis.com/drive/v3/files")
                .queryParam("q", query)
                .queryParam("fields", "files(id,name,modifiedTime)")
                .build()
                .encode()
                .toUri();
        // 4. Faire l'appel HTTP GET

        log.info(" URI: {}", uri);

        ResponseEntity<String> response = restTemplate.exchange(
                uri, HttpMethod.GET, new HttpEntity<>(headers), String.class);

        log.info(" Response: {}", response.getBody());
        if (response.getBody() == null) {
            return null;
        }
        // 5. Parser la réponse JSON
        JsonNode root = objectMapper.readTree(response.getBody());
        JsonNode files = root.get("files");

        if (files == null || files.size() == 0) {
            log.warn("No files found matching: {}", fileName);
            return null;
        }
// 6. Retourner l'ID du premier fichier trouvé
        return files.get(0).get("id").asText();
    }
    //Pourquoi cette méthode? Google Drive identifie les fichiers par un ID unique (pas par nom). Donc on cherche d'abord l'ID, puis on télécharge.




    // ── Récupérer la date de modification ────────────────────────────────
    public String getFileModifiedTime(String fileName) throws IOException {
        String fileId = searchFileInFolder(fileName);
        if (fileId == null) {
            return null;
        }

        String metadataUrl = "https://www.googleapis.com/drive/v3/files/" + fileId +
                "?fields=modifiedTime";

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(getAccessToken());

        HttpEntity<String> entity = new HttpEntity<>(headers);
        ResponseEntity<String> response = restTemplate.getForEntity(metadataUrl, String.class);

        if (response.getBody() == null) {
            return null;
        }

        JsonNode root = objectMapper.readTree(response.getBody());
        return root.get("modifiedTime").asText();
    }
}