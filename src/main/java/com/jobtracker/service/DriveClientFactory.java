package com.jobtracker.service;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.docs.v1.Docs;
import com.google.api.services.drive.Drive;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.security.GeneralSecurityException;

@Component
public class DriveClientFactory {
    private static final String APPLICATION_NAME = "JobApplyTracker";
    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int READ_TIMEOUT_MS = 30_000;
    private final HttpTransport httpTransport;
    private final JsonFactory jsonFactory = GsonFactory.getDefaultInstance();

    public DriveClientFactory() throws GeneralSecurityException, IOException {
        this.httpTransport = GoogleNetHttpTransport.newTrustedTransport();
    }

    public Drive create(String accessToken) {
        return new Drive.Builder(httpTransport, jsonFactory, createRequestInitializer(accessToken)).setApplicationName(APPLICATION_NAME).build();
    }

    public Docs createDocs(String accessToken) {
        return new Docs.Builder(httpTransport, jsonFactory, createRequestInitializer(accessToken)).setApplicationName(APPLICATION_NAME).build();
    }

    private HttpRequestInitializer createRequestInitializer(String accessToken) {
        return request -> {
            request.getHeaders().setAuthorization("Bearer " + accessToken);
            request.setConnectTimeout(CONNECT_TIMEOUT_MS);
            request.setReadTimeout(READ_TIMEOUT_MS);
        };
    }
}