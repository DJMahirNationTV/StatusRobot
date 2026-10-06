package com.djmahirnationtv.status.backend.integration;

import com.djmahirnationtv.status.backend.monitor.repository.MonitorRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class IntegrationService {
    private final DiscordIntegrationRepository integrations;
    private final MonitorRepository monitors;
    private final WebhookSecrets secrets;
    private final DiscordWebhookClient discord;

    public IntegrationService(DiscordIntegrationRepository integrations, MonitorRepository monitors,
                              WebhookSecrets secrets, DiscordWebhookClient discord) {
        this.integrations = integrations;
        this.monitors = monitors;
        this.secrets = secrets;
        this.discord = discord;
    }

    public record Summary(Long id, String name) {
        static Summary from(DiscordIntegration integration) { return new Summary(integration.getId(), integration.getName()); }
    }
    public record Listing(boolean configured, List<Summary> integrations) {}

    public Listing list(Long ownerId) {
        return new Listing(secrets.configured(), integrations.findByOwnerIdOrderByIdDesc(ownerId).stream().map(Summary::from).toList());
    }

    @Transactional
    public Summary save(Long id, String name, String webhookUrl, Long ownerId) {
        DiscordIntegration integration = id == null ? new DiscordIntegration() : owned(id, ownerId);
        if (id == null || (webhookUrl != null && !webhookUrl.isBlank())) {
            String url = webhookUrl == null ? "" : webhookUrl.strip();
            if (!DiscordWebhookClient.validUrl(url)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Use a Discord webhook URL from discord.com!!");
            integration.setEncryptedWebhook(secrets.encrypt(url));
        }
        integration.setOwnerId(ownerId);
        integration.setName(name.strip());
        return Summary.from(integrations.save(integration));
    }

    public Set<DiscordIntegration> selection(List<Long> ids, Long ownerId) {
        Set<DiscordIntegration> selected = new HashSet<>();
        if (ids != null) for (Long id : new HashSet<>(ids)) selected.add(owned(id, ownerId));
        return selected;
    }

    public void test(Long id, Long ownerId) {
        var integration = owned(id, ownerId);
        if (!discord.send(secrets.decrypt(integration.getEncryptedWebhook()), "StatusRobot: Your Discord integration is connected."))
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Discord did not accept the message. Check your webhook or try again later.");
    }

    @Transactional
    public void delete(Long id, Long ownerId) {
        var integration = owned(id, ownerId);
        for (var monitor : monitors.findByIntegrationsId(id)) {
            monitor.getIntegrations().removeIf(item -> item.getId().equals(id));
        }
        monitors.flush();
        integrations.delete(integration);
    }

    private DiscordIntegration owned(Long id, Long ownerId) {
        return integrations.findById(id).filter(item -> ownerId.equals(item.getOwnerId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Integration not found"));
    }
}
