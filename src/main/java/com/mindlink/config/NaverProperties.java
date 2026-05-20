package com.mindlink.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "naver.openapi")
public class NaverProperties {

    private String clientId = "";
    private String clientSecret = "";
    private String localSearchUrl = "https://openapi.naver.com/v1/search/local.json";
    private String defaultQuery = "심리상담센터";
    private int display = 20;

    public boolean isConfigured() {
        return clientId != null && !clientId.isBlank()
                && clientSecret != null && !clientSecret.isBlank();
    }

    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }

    public String getClientSecret() { return clientSecret; }
    public void setClientSecret(String clientSecret) { this.clientSecret = clientSecret; }

    public String getLocalSearchUrl() { return localSearchUrl; }
    public void setLocalSearchUrl(String localSearchUrl) { this.localSearchUrl = localSearchUrl; }

    public String getDefaultQuery() { return defaultQuery; }
    public void setDefaultQuery(String defaultQuery) { this.defaultQuery = defaultQuery; }

    public int getDisplay() { return display; }
    public void setDisplay(int display) { this.display = display; }
}
