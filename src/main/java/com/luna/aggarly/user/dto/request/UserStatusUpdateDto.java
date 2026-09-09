package com.luna.aggarly.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserStatusUpdateDto {
    @NotBlank(message = "Status cannot be empty")
    private String status;
    private String reason;
}
