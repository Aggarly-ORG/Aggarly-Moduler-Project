package com.luna.aggarly.user.security;

import com.luna.aggarly.user.entity.User;
import com.luna.aggarly.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserPrincipalDetailsServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserPrincipalDetailsService userPrincipalDetailsService;

    @Test
    @DisplayName("Should load user details by email successfully")
    void shouldLoadUserByUsernameSuccessfully() {
        String email = "john@example.com";
        User user = User.builder().email(email).username("john").build();
        user.setId(UUID.randomUUID());

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));

        UserDetails userDetails = userPrincipalDetailsService.loadUserByUsername(email);

        assertThat(userDetails).isNotNull();
        assertThat(userDetails.getUsername()).isEqualTo(email);
    }

    @Test
    @DisplayName("Should throw UsernameNotFoundException when email does not exist")
    void shouldThrowUsernameNotFoundExceptionWhenEmailDoesNotExist() {
        String email = "missing@example.com";
        when(userRepository.findByEmail(email)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userPrincipalDetailsService.loadUserByUsername(email))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessageContaining("User not found with email: " + email);
    }
}
