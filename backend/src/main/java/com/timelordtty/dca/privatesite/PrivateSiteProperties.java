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
    // Maximum reading window; issuance still stops at the verified JWT's earlier expiry.
    private long sessionTtlSeconds = 86400;
    private String publicationRoot;
    private int protocolVersion;

    boolean sessionsEnabled() {
        return enabled && ownerUserId != null && ownerUserId > 0
                && sessionCapacity > 0 && sessionCapacity <= 1024
                && sessionTtlSeconds > 0 && sessionTtlSeconds <= 86400;
    }
}
