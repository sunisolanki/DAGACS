package com.dagacs.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttendanceMarkRequestDTO {

    @NotNull(message = "Session ID is required")
    private Long sessionId;

    @NotEmpty(message = "At least one attendance item is required")
    @Valid
    private List<@NotNull(message = "Each attendance item must be provided") AttendanceMarkItemDTO> items;
}
