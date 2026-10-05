package com.djmahirnationtv.status.backend.integration;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "discord_integrations")
@Getter
@Setter
public class DiscordIntegration {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private Long ownerId;
    @Column(nullable = false, length = 80)
    private String name;
    @Column(nullable = false, length = 1024)
    private String encryptedWebhook;
}
