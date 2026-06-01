package com.jobtracker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.gpt-fallback-auth")
public class GptFallbackAuthProperties {

    private boolean enabled = false;
    private String token = "";
    private String accountEmail = "gpt-fallback@jobtracker.local";
    private String accountName = "GPT Fallback";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getAccountEmail() {
        return accountEmail;
    }

    public void setAccountEmail(String accountEmail) {
        this.accountEmail = accountEmail;
    }

    public String getAccountName() {
        return accountName;
    }

    public void setAccountName(String accountName) {
        this.accountName = accountName;
    }

    public boolean isConfigured() {
        return enabled && token != null && !token.isBlank() && accountEmail != null && !accountEmail.isBlank();
    }
}
