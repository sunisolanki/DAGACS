package com.dagacs.repository;

import com.dagacs.entity.AttendanceRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AttendanceRecordAggregationRepository extends JpaRepository<AttendanceRecord, Long> {

    @Query("SELECT COUNT(ar) FROM AttendanceRecord ar WHERE ar.student.id = :studentId")
    long countByStudentId(@Param("studentId") Long studentId);

    @Query("SELECT COUNT(ar) FROM AttendanceRecord ar WHERE ar.student.id = :studentId AND ar.isPresent = true")
    long countByStudentIdAndIsPresentTrue(@Param("studentId") Long studentId);

    @Query("SELECT COUNT(ar) FROM AttendanceRecord ar WHERE ar.student.id = :studentId AND ar.subject.id = :subjectId")
    long countByStudentIdAndSubjectId(@Param("studentId") Long studentId, @Param("subjectId") Long subjectId);

    @Query("SELECT COUNT(ar) FROM AttendanceRecord ar WHERE ar.student.id = :studentId AND ar.subject.id = :subjectId AND ar.isPresent = true")
    long countByStudentIdAndSubjectIdAndIsPresentTrue(@Param("studentId") Long studentId, @Param("subjectId") Long subjectId);

    @Query("SELECT COUNT(ar) FROM AttendanceRecord ar "
            + "WHERE ar.student.id = :studentId "
            + "AND (:startDate IS NULL OR ar.date >= :startDate) "
            + "AND (:endDate IS NULL OR ar.date <= :endDate)")
    long countRecordedByStudentAndDateRange(@Param("studentId") Long studentId,
                                            @Param("startDate") String startDate,
                                            @Param("endDate") String endDate);

    @Query("SELECT COUNT(ar) FROM AttendanceRecord ar "
            + "WHERE ar.student.id = :studentId AND ar.isPresent = true "
            + "AND (:startDate IS NULL OR ar.date >= :startDate) "
            + "AND (:endDate IS NULL OR ar.date <= :endDate)")
    long countPresentByStudentAndDateRange(@Param("studentId") Long studentId,
                                           @Param("startDate") String startDate,
                                           @Param("endDate") String endDate);

    @Query("SELECT COUNT(ar) FROM AttendanceRecord ar "
            + "WHERE ar.student.id = :studentId AND ar.subject.id = :subjectId "
            + "AND (:startDate IS NULL OR ar.date >= :startDate) "
            + "AND (:endDate IS NULL OR ar.date <= :endDate)")
    long countRecordedByStudentAndSubjectAndDateRange(@Param("studentId") Long studentId,
                                                      @Param("subjectId") Long subjectId,
                                                      @Param("startDate") String startDate,
                                                      @Param("endDate") String endDate);

    @Query("SELECT COUNT(ar) FROM AttendanceRecord ar "
            + "WHERE ar.student.id = :studentId AND ar.subject.id = :subjectId AND ar.isPresent = true "
            + "AND (:startDate IS NULL OR ar.date >= :startDate) "
            + "AND (:endDate IS NULL OR ar.date <= :endDate)")
    long countPresentByStudentAndSubjectAndDateRange(@Param("studentId") Long studentId,
                                                     @Param("subjectId") Long subjectId,
                                                     @Param("startDate") String startDate,
                                                     @Param("endDate") String endDate);
}
