package com.djmahirnationtv.status.backend.statuspage;

import com.djmahirnationtv.status.backend.monitor.model.Monitor;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "status_pages")
@Getter
@Setter
public class StatusPage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Version
    private long version;
    @Column(nullable = false)
    private Long ownerId;
    @Column(nullable = false, length = 100)
    private String name;
    @Column(nullable = false, unique = true, length = 60)
    private String slug;
    @Column(nullable = false, length = 500)
    private String description = "";
    @Column(unique = true)
    private Integer defaultSlot;
    @ManyToMany
    @JoinTable(name = "status_page_monitors", joinColumns = @JoinColumn(name = "page_id"),
            inverseJoinColumns = @JoinColumn(name = "monitor_id"))
    private Set<Monitor> monitors = new HashSet<>();

    public boolean isPinnedDefault() { return defaultSlot != null; }
    public void pinDefault(boolean pinned) { defaultSlot = pinned ? 1 : null; }
}
