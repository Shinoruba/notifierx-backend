package com.project.notifierx.repository;

import com.project.notifierx.domain.Tier;
import com.project.notifierx.domain.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UserRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private UserRepository userRepository;


    private User buildUser(String name, String apiKey, Tier tier) {
        return User.builder()
                .name(name)
                .apiKey(apiKey)
                .tier(tier)
                .build();
    }


    @Test
    @DisplayName("save() persists a User and generates a UUID primary key")
    void save_persistsUser_andGeneratesUuid() {
        User user = buildUser("Alice", "key-alice-001", Tier.FREE);

        User saved = userRepository.save(user);
        entityManager.flush();

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getName()).isEqualTo("Alice");
        assertThat(saved.getApiKey()).isEqualTo("key-alice-001");
        assertThat(saved.getTier()).isEqualTo(Tier.FREE);
    }

    @Test
    @DisplayName("save() auto-populates createdAt and updatedAt timestamps")
    void save_setsAuditTimestamps() {
        User user = buildUser("Bob", "key-bob-001", Tier.PREMIUM);

        User saved = userRepository.save(user);
        entityManager.flush();

        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("findByApiKey() returns the correct User when the key exists")
    void findByApiKey_returnsUser_whenKeyExists() {
        User user = buildUser("Charlie", "key-charlie-unique-999", Tier.FREE);
        entityManager.persistAndFlush(user);

        Optional<User> result = userRepository.findByApiKey("key-charlie-unique-999");

        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo("Charlie");
        assertThat(result.get().getTier()).isEqualTo(Tier.FREE);
    }

    @Test
    @DisplayName("findByApiKey() returns empty Optional when the key does not exist")
    void findByApiKey_returnsEmpty_whenKeyDoesNotExist() {
        Optional<User> result = userRepository.findByApiKey("non-existent-key");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Tier.FREE has rateLimitPerMinute of 5")
    void tier_free_hasCorrectRateLimit() {
        assertThat(Tier.FREE.getRateLimitPerMinute()).isEqualTo(5);
    }

    @Test
    @DisplayName("Tier.PREMIUM has rateLimitPerMinute of 100")
    void tier_premium_hasCorrectRateLimit() {
        assertThat(Tier.PREMIUM.getRateLimitPerMinute()).isEqualTo(100);
    }

    @Test
    @DisplayName("Tier is persisted as a STRING value in the database")
    void tier_persistedAsString() {
        User user = buildUser("Diana", "key-diana-001", Tier.PREMIUM);
        entityManager.persistAndFlush(user);
        entityManager.clear();

        User found = userRepository.findByApiKey("key-diana-001").orElseThrow();
        assertThat(found.getTier()).isEqualTo(Tier.PREMIUM);
        assertThat(found.getTier().getRateLimitPerMinute()).isEqualTo(100);
    }
}