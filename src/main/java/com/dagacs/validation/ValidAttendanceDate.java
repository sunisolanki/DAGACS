package com.dagacs.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Constraint(validatedBy = ValidAttendanceDateValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidAttendanceDate {
    String message() default "Date must be a valid YYYY-MM-DD date";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
