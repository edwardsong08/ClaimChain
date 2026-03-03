package com.claimchain.backend.model;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "admin_bootstrap_state")
public class AdminBootstrapState {

    @Id
    private Integer id;

    @Column(name = "used_at")
    private Instant usedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "used_by_user_id")
    private User usedByUser;

    public AdminBootstrapState() {}

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Instant getUsedAt() {
        return usedAt;
    }

    public void setUsedAt(Instant usedAt) {
        this.usedAt = usedAt;
    }

    public User getUsedByUser() {
        return usedByUser;
    }

    public void setUsedByUser(User usedByUser) {
        this.usedByUser = usedByUser;
    }
}
