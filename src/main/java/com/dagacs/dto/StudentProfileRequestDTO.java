package com.dagacs.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentProfileRequestDTO {

    @Size(max = 100, message = "Father name must not exceed 100 characters")
    private String fatherName;

    @Size(max = 100, message = "Mother name must not exceed 100 characters")
    private String motherName;

    @Size(max = 10, message = "Gender must not exceed 10 characters")
    private String gender;

    @Email(message = "Personal email must be a valid email address")
    @Size(max = 255, message = "Personal email must not exceed 255 characters")
    private String personalEmail;
}

