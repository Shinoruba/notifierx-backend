package com.project.notifierx.service;

import com.project.notifierx.domain.NotificationAudit;
import com.project.notifierx.domain.NotificationStatus;
import com.project.notifierx.domain.User;
import com.project.notifierx.dto.SendNotificationRequest;
import com.project.notifierx.dto.SendNotificationResponse;
import com.project.notifierx.exception.RateLimitExceededException;
import com.project.notifierx.exception.UserNotFoundException;
import com.project.notifierx.repository.NotificationAuditRepository;
import com.project.notifierx.repository.UserRepository;
import com.project.notifierx.service.ratelimit.RateLimiter;
import com.project.notifierx.service.strategy.NotificationResult;
import com.project.notifierx.service.strategy.NotificationStrategy;
import com.project.notifierx.service.strategy.NotificationStrategyFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class NotificationServiceImpl implements NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationServiceImpl.class);

    private static final long RETRY_AFTER_SECONDS = 60L;

    private final UserRepository userRepository;
    private final NotificationAuditRepository auditRepository;
    private final RateLimiter rateLimiter;
    private final NotificationStrategyFactory strategyFactory;

    public NotificationServiceImpl(
            UserRepository userRepository,
            NotificationAuditRepository auditRepository,
            RateLimiter rateLimiter,
            NotificationStrategyFactory strategyFactory) {
        this.userRepository = userRepository;
        this.auditRepository = auditRepository;
        this.rateLimiter = rateLimiter;
        this.strategyFactory = strategyFactory;
    }

    @Override
    @Transactional
    public SendNotificationResponse sendNotification(String apiKey,
                                                     SendNotificationRequest request) {
        User user = resolveUser(apiKey);
        enforceRateLimit(apiKey, user);
        NotificationStrategy strategy = strategyFactory.getStrategy(request.channel());
        log.info("[PIPELINE] Dispatching {} notification to '{}' for user '{}'",
                request.channel(), request.recipient(), user.getName());
        NotificationResult result = strategy.send(request.recipient(), request.payload());
        NotificationAudit audit = persistAudit(user, request, result);
        return buildResponse(audit, result);
    }

    private User resolveUser(String apiKey) {
        return userRepository.findByApiKey(apiKey)
                .orElseThrow(() -> {
                    log.warn("[PIPELINE] Unknown API key: '{}'", apiKey);
                    return new UserNotFoundException(apiKey);
                });
    }

    private void enforceRateLimit(String apiKey, User user) {
        if (!rateLimiter.isAllowed(apiKey, user.getTier())) {
            log.warn("[PIPELINE] Rate limit exceeded for API key '{}' (tier: {})",
                    apiKey, user.getTier());
            throw new RateLimitExceededException(apiKey, user.getTier(), RETRY_AFTER_SECONDS);
        }
    }

    private NotificationAudit persistAudit(User user,
                                            SendNotificationRequest request,
                                            NotificationResult result) {
        NotificationStatus status = result.success()
                ? NotificationStatus.SENT
                : NotificationStatus.FAILED;

        NotificationAudit audit = NotificationAudit.builder()
                .userId(user.getId())
                .channel(request.channel())
                .recipient(request.recipient())
                .payload(request.payload())
                .status(status)
                .errorMessage(result.errorMessage())
                .build();

        NotificationAudit saved = auditRepository.save(audit);
        log.info("[PIPELINE] Audit persisted — id: {}, status: {}", saved.getId(), status);
        return saved;
    }

    private SendNotificationResponse buildResponse(NotificationAudit audit,
                                                    NotificationResult result) {
        return new SendNotificationResponse(
                audit.getId(),
                audit.getStatus(),
                result.providerMessageId(),
                result.errorMessage(),
                Instant.now()
        );
    }
}