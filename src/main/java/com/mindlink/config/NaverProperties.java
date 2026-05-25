package com.mindlink.config;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.env.Environment;

@ConfigurationProperties(prefix = "naver.openapi")
public class NaverProperties implements InitializingBean {

    private String clientId = "";
    private String clientSecret = "";
    private String localSearchUrl = "https://openapi.naver.com/v1/search/local.json";
    private String defaultQuery = "심리상담센터";
    private int display = 20;

    @Autowired
    private Environment environment;

    @Override
    public void afterPropertiesSet() {
        if (!isRealKey(clientId)) {
            clientId = firstRealKey("naver.openapi.client-id",
                    "NAVER_OPENAPI_CLIENT_ID", "NAVER_API_CLIENT_ID");
        }
        if (!isRealKey(clientSecret)) {
            clientSecret = firstRealKey("naver.openapi.client-secret",
                    "NAVER_OPENAPI_CLIENT_SECRET", "NAVER_API_CLIENT_SECRET");
        }
    }

    private String firstRealKey(String... keys) {
        for (String key : keys) {
            if (environment != null) {
                String v = environment.getProperty(key);
                if (isRealKey(v)) {
                    return v.trim();
                }
            }
            String env = System.getenv(key);
            if (isRealKey(env)) {
                return env.trim();
            }
        }
        return "";
    }

    public boolean isConfigured() {
        return isRealKey(clientId) && isRealKey(clientSecret);
    }

    private static boolean isRealKey(String v) {
        if (v == null || v.isBlank()) return false;
        return !"NOT_SET".equalsIgnoreCase(v.trim());
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
