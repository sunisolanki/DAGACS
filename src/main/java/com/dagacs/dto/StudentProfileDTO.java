package com.dagacs.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentProfileDTO {

    private String personalEmail;
    private String rollNumber;
    private String enrollmentNumber;
    private String name;
    private String gender;
    private String fatherName;
    private String motherName;
    private String photoUrl;
    private Integer age;
    private String admissionDate;
    private String status;
    private String batchName;
    private String programName;
    private String sectionName;
    private String academicSessionName;
}

