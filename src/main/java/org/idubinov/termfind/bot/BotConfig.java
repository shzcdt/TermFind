package org.idubinov.termfind.bot;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "bot")
public record BotConfig(String username, String token, long adminId) {
}
