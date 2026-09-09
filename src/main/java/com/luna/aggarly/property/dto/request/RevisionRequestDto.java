package com.luna.aggarly.property.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RevisionRequestDto {
    @NotBlank(message = "Revision notes cannot be empty")
    private String notes;
}
