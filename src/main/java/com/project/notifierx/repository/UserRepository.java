package com.project.notifierx.repository;

import com.project.notifierx.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByApiKey(String apiKey);
}