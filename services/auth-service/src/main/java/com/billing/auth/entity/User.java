package com.billing.auth.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name="users",schema="auth")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class User{

    @Id
    @GeneratedValue(strategy=GenerationType.UUID)
    private UUID id;

    @Column(name="username",unique=true,nullable=false)
    private String username;

    @Column(name="email",unique=true,nullable=false)
    private String email;

    @Column(name="password_hash",nullable=false)
    private String passwordHash;

    @Column(name="tenant_id",nullable=false)
    private String tenantId;

    @Column(name="role",nullable=false)
    private String role;

    @Column(name="is_email_verified",nullable=false)
    private Boolean isEmailVerified;

    @Column(name="is_active",nullable=false)
    private Boolean isActive;

    @Column(name="created_at",nullable=false)
    private LocalDateTime createdAt;

    @Column(name="updated_at",nullable=false)
    private LocalDateTime updatedAt;

}