package com.backend.Ai_Gateway.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = "user")
@Getter
@Setter
public class PromptTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 500)
    private String description;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    private Instant createdAt;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    private User user;
}
