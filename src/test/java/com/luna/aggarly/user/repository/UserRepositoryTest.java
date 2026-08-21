package com.luna.aggarly.user.repository;

import com.luna.aggarly.user.entity.enums.AuthProvider;
import com.luna.aggarly.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class UserRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("Should find user by email and username")
    void shouldFindUserByEmailAndUsername() {
        User user = User.builder()
                .email("john@example.com")
                .username("john_doe")
                .passwordHash("password")
                .firstName("John")
                .lastName("Doe")
                .emailVerified(true)
                .authProvider(AuthProvider.LOCAL)
                .build();

        user.setCreatedAt(java.time.Instant.now());
        entityManager.persistAndFlush(user);

        Optional<User> foundByEmail = userRepository.findByEmail("john@example.com");
        assertThat(foundByEmail).isPresent();
        assertThat(foundByEmail.get().getUsername()).isEqualTo("john_doe");

        assertThat(userRepository.existsByEmail("john@example.com")).isTrue();
        assertThat(userRepository.existsByEmail("other@example.com")).isFalse();

        Optional<User> foundByUsername = userRepository.findByUsername("john_doe");
        assertThat(foundByUsername).isPresent();
        assertThat(userRepository.existsByUsername("john_doe")).isTrue();
    }

    @Test
    @DisplayName("Should respect soft delete @SQLRestriction")
    void shouldRespectSoftDeleteRestriction() {
        User user = User.builder()
                .email("deleted@example.com")
                .username("deleted_user")
                .passwordHash("password")
                .authProvider(AuthProvider.LOCAL)
                .build();
        user.setCreatedAt(java.time.Instant.now());

        user = entityManager.persistAndFlush(user);

        // Soft delete user
        user.setDeleted(true);
        entityManager.persistAndFlush(user);
        entityManager.clear();

        Optional<User> activeUser = userRepository.findByEmail("deleted@example.com");
        assertThat(activeUser).isEmpty();

        Optional<User> anyUser = userRepository.findAnyByEmail("deleted@example.com");
        assertThat(anyUser).isPresent();
        assertThat(anyUser.get().isDeleted()).isTrue();
    }
}
