package com.backend.Ai_Gateway.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;


@Entity
@NoArgsConstructor
@AllArgsConstructor
@ToString
@Getter
@Setter
public class PromptTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    private String name;
    private String description;
    private String content;
    private Instant createdAt;

    @ManyToOne
    private User user;

}
