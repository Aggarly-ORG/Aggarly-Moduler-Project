package com.luna.aggarly.user.repository;

import com.luna.aggarly.user.entity.Role;
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
class RoleRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private RoleRepository roleRepository;

    @Test
    @DisplayName("Should find role by name")
    void shouldFindRoleByName() {
        Role role = Role.builder().name("GUEST").build();
        entityManager.persistAndFlush(role);

        Optional<Role> foundRole = roleRepository.findByName("GUEST");

        assertThat(foundRole).isPresent();
        assertThat(foundRole.get().getName()).isEqualTo("GUEST");
    }
}
