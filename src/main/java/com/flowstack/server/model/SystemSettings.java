package com.flowstack.server.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@ConfigurationProperties("flowstack.server")
@Component
@Data
@NoArgsConstructor
public class SystemSettings {

    private Restic restic;

    @Data
    public static class Restic {
        private String backupPath;

        private String backupPassword;
    }
}
