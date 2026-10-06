package com.djmahirnationtv.status.backend.integration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class DiscordAlertListener {
    private static final Logger log = LoggerFactory.getLogger(DiscordAlertListener.class);
    private final DiscordIntegrationRepository integrations;
    private final WebhookSecrets secrets;
    private final DiscordWebhookClient discord;

    public DiscordAlertListener(DiscordIntegrationRepository integrations, WebhookSecrets secrets, DiscordWebhookClient discord) {
        this.integrations = integrations;
        this.secrets = secrets;
        this.discord = discord;
    }

    @TransactionalEventListener
    public void statusChanged(MonitorStatusChanged event) {
        try {
            for (var integration : integrations.findForMonitor(event.monitorId(), event.ownerId())) {
                try {
                    String content = "StatusRobot: " + event.name() + (event.down() ? " is down." : " is back online.");
                    if (!discord.send(secrets.decrypt(integration.getEncryptedWebhook()), content))
                        log.warn("Discord alert failed for integration {}", integration.getId());
                } catch (RuntimeException exception) {
                    log.warn("Discord alert unavailable for integration {}", integration.getId());
                }
            }
        } catch (RuntimeException exception) {
            log.warn("Could not load Discord integrations for monitor {}", event.monitorId());
        }
    }
}
