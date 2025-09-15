package com.company.connector.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.Firestore;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.cloud.FirestoreClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.io.FileInputStream;
import java.io.IOException;

/**
 * Firebase configuration for Firestore setup.
 */
@Configuration
public class FirebaseConfiguration {
    
    private static final Logger logger = LoggerFactory.getLogger(FirebaseConfiguration.class);
    
    @Bean
    @Profile("!test")
    @ConditionalOnProperty(name = "firebase.service-account-path")
    public Firestore firestore(FirebaseConfig firebaseConfig) throws IOException {
        logger.info("Initializing Firebase with project ID: {}", firebaseConfig.projectId());
        
        GoogleCredentials credentials = GoogleCredentials
            .fromStream(new FileInputStream(firebaseConfig.serviceAccountPath()));
        
        FirebaseOptions options = FirebaseOptions.builder()
            .setCredentials(credentials)
            .setProjectId(firebaseConfig.projectId())
            .build();
        
        if (FirebaseApp.getApps().isEmpty()) {
            FirebaseApp.initializeApp(options);
            logger.info("Firebase app initialized successfully");
        }
        
        return FirestoreClient.getFirestore();
    }
}