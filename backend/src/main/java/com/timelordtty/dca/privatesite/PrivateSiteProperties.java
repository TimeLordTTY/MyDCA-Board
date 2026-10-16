package com.timelordtty.dca.privatesite;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** No owner, publication root or protocol is inferred from an existing installation. */
@Getter
@Setter
@ConfigurationProperties(prefix = "private-sites")
public class PrivateSiteProperties {
    private boolean enabled;
    private Long ownerUserId;
    private int sessionCapacity = 128;
    private long sessionTtlSeconds = 900;
    private String publicationRoot;
    private int protocolVersion;

    boolean sessionsEnabled() {
        return enabled && ownerUserId != null && ownerUserId > 0
                && sessionCapacity > 0 && sessionCapacity <= 1024
                && sessionTtlSeconds > 0 && sessionTtlSeconds <= 3600;
    }
}
