package com.project.notifierx.repository;

import com.project.notifierx.domain.NotificationAudit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface NotificationAuditRepository extends JpaRepository<NotificationAudit, UUID> {
}