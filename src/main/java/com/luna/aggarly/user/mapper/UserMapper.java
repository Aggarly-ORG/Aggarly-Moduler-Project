package com.luna.aggarly.user.mapper;

import com.luna.aggarly.user.dto.response.UserProfileResponse;
import com.luna.aggarly.user.dto.response.UserProfileSummaryResponse;
import com.luna.aggarly.user.entity.Role;
import com.luna.aggarly.user.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.Set;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring")
public interface UserMapper {

    @Mapping(target = "displayName", source = "user", qualifiedByName = "resolveDisplayName")
    @Mapping(target = "roles", source = "roles", qualifiedByName = "mapRolesToStringSet")
    UserProfileResponse toProfileResponse(User user);

    @Mapping(target = "displayName", source = "user", qualifiedByName = "resolveDisplayName")
    UserProfileSummaryResponse toSummaryResponse(User user);

    @Named("mapRolesToStringSet")
    default Set<String> mapRolesToStringSet(Set<Role> roles) {
        if (roles == null || roles.isEmpty()) {
            return Set.of("GUEST");
        }
        return roles.stream()
                .map(Role::getName)
                .collect(Collectors.toSet());
    }

    @Named("resolveDisplayName")
    default String resolveDisplayName(User user) {
        if (user == null) {
            return null;
        }
        if (user.getDisplayName() != null && !user.getDisplayName().isBlank()) {
            return user.getDisplayName();
        }
        if (user.getFirstName() != null && !user.getFirstName().isBlank()) {
            return user.getFirstName() + (user.getLastName() != null ? " " + user.getLastName() : "");
        }
        return user.getUsername();
    }
}
