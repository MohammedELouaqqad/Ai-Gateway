package com.backend.Ai_Gateway.entity;


import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class ApiKey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String keyHash;
    private String keyPrefix;
    private String name;
    private boolean enabled;
    private Instant createdAt;

    /** Nullable — null means the key never expires. Set once at creation, never updated. */
    private Instant expiresAt;

    @ManyToOne
    private User user;

}
