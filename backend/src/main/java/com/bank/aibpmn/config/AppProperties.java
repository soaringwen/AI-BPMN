package com.bank.aibpmn.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private Ai ai = new Ai();
    private Bpmn bpmn = new Bpmn();
    private Cors cors = new Cors();

    public static class Ai {
        private boolean mockEnabled = true;
        private int timeoutSeconds = 60;
        private int maxRetries = 1;
        private int maxInputCharacters = 30000;
        private int maxConversationMessages = 10;
        private int maxClarificationQuestions = 5;

        public boolean isMockEnabled() {
            return mockEnabled;
        }

        public void setMockEnabled(boolean mockEnabled) {
            this.mockEnabled = mockEnabled;
        }

        public int getTimeoutSeconds() {
            return timeoutSeconds;
        }

        public void setTimeoutSeconds(int timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
        }

        public int getMaxRetries() {
            return maxRetries;
        }

        public void setMaxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
        }

        public int getMaxInputCharacters() {
            return maxInputCharacters;
        }

        public void setMaxInputCharacters(int maxInputCharacters) {
            this.maxInputCharacters = maxInputCharacters;
        }

        public int getMaxConversationMessages() {
            return maxConversationMessages;
        }

        public void setMaxConversationMessages(int maxConversationMessages) {
            this.maxConversationMessages = maxConversationMessages;
        }

        public int getMaxClarificationQuestions() {
            return maxClarificationQuestions;
        }

        public void setMaxClarificationQuestions(int maxClarificationQuestions) {
            this.maxClarificationQuestions = maxClarificationQuestions;
        }
    }

    public static class Bpmn {
        private String targetNamespace = "urn:bank:ai-bpmn";
        private String defaultDialect = "STANDARD_BPMN_20";
        private long maxFileSizeBytes = 10485760;
        private boolean strictExtensionValidation = true;
        private boolean defaultIsExecutable = false;

        public String getTargetNamespace() {
            return targetNamespace;
        }

        public void setTargetNamespace(String targetNamespace) {
            this.targetNamespace = targetNamespace;
        }

        public String getDefaultDialect() {
            return defaultDialect;
        }

        public void setDefaultDialect(String defaultDialect) {
            this.defaultDialect = defaultDialect;
        }

        public long getMaxFileSizeBytes() {
            return maxFileSizeBytes;
        }

        public void setMaxFileSizeBytes(long maxFileSizeBytes) {
            this.maxFileSizeBytes = maxFileSizeBytes;
        }

        public boolean isStrictExtensionValidation() {
            return strictExtensionValidation;
        }

        public void setStrictExtensionValidation(boolean strictExtensionValidation) {
            this.strictExtensionValidation = strictExtensionValidation;
        }

        public boolean isDefaultIsExecutable() {
            return defaultIsExecutable;
        }

        public void setDefaultIsExecutable(boolean defaultIsExecutable) {
            this.defaultIsExecutable = defaultIsExecutable;
        }
    }

    public static class Cors {
        private List<String> allowedOrigins = List.of("http://localhost:5173");

        public List<String> getAllowedOrigins() {
            return allowedOrigins;
        }

        public void setAllowedOrigins(List<String> allowedOrigins) {
            this.allowedOrigins = allowedOrigins;
        }
    }

    public Ai getAi() {
        return ai;
    }

    public void setAi(Ai ai) {
        this.ai = ai;
    }

    public Bpmn getBpmn() {
        return bpmn;
    }

    public void setBpmn(Bpmn bpmn) {
        this.bpmn = bpmn;
    }

    public Cors getCors() {
        return cors;
    }

    public void setCors(Cors cors) {
        this.cors = cors;
    }
}
