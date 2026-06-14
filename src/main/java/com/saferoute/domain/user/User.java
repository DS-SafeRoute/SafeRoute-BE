package com.saferoute.domain.user;

import com.saferoute.domain.training.entity.TrainingScenario;
import com.saferoute.domain.training.entity.TrainingSession;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Getter
@Entity
@Table(name = "users")
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

  @Id
  @GeneratedValue
  private UUID id;

  @NotBlank
  @Size(min = 2, max = 20)
  @Column(nullable = false, length = 20)
  private String username;

  @NotBlank
  @Size(min = 8, max = 100)
  @Column(nullable = false, length = 100)
  private String password;

  @Email
  @NotBlank
  @Column(nullable = false, unique = true, length = 255)
  private String email;

  //유저 타입 (MANAGER, NORMAL)
  @NotNull
  @Enumerated(EnumType.STRING)
  @Column(name = "user_role", nullable = false, length = 20)
  private UserRole role;

  @NotBlank
  @Size(min = 5, max = 20)
  @Column(name = "school_name", nullable = false, length = 20)
  private String schoolName;

  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @LastModifiedDate
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @OneToMany(mappedBy = "admin", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<TrainingScenario> trainingScenarios = new ArrayList<>();

  @OneToMany(mappedBy = "admin", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<TrainingSession> trainingSessions = new ArrayList<>();
}