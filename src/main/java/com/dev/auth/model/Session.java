package com.dev.auth.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
    name = "user_sessions",
    indexes = {
        @Index(
            name = "idx_user_sessions_user_id",
            columnList = "user_id"
        ),
        @Index(
            name = "idx_user_sessions_expiry",
            columnList = "expires_at"
        )
    }
)
public class Session {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "user_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_user_sessions_user")
    )
    private User user;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "device_name", length = 100)
    private String deviceName;

    protected Session() {
        // Required by JPA.
    }

    public Session(User user, Instant expiresAt, String deviceName) {
        this.user = user;
        this.expiresAt = expiresAt;
        this.deviceName = deviceName;
    }

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();

        if (createdAt == null) {
            createdAt = now;
        }

        if (lastSeenAt == null) {
            lastSeenAt = now;
        }
    }

    public boolean isActive() {
        return revokedAt == null
            && expiresAt.isAfter(Instant.now());
    }

    public void revoke() {
        if (revokedAt == null) {
            revokedAt = Instant.now();
        }
    }

    public void updateLastSeen() {
        lastSeenAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getLastSeenAt() {
        return lastSeenAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public String getDeviceName() {
        return deviceName;
    }
}
