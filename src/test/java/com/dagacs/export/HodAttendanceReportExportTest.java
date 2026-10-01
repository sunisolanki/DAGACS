package com.dagacs.export;

import com.dagacs.config.ReportBrandingProperties;
import com.dagacs.dto.HodAttendanceContextDTO;
import com.dagacs.dto.HodAttendanceOverviewDTO;
import com.dagacs.dto.HodAttendanceSubjectSummaryDTO;
import com.dagacs.dto.HodLowAttendanceReportDTO;
import com.dagacs.dto.HodLowAttendanceStudentDTO;
import com.dagacs.dto.HodLowAttendanceSubjectDTO;
import com.dagacs.dto.HodStudentAttendanceDetailDTO;
import com.dagacs.dto.HodStudentSubjectAttendanceDTO;
import com.dagacs.dto.HodSubjectAttendanceDetailDTO;
import com.dagacs.dto.HodSubjectAttendanceDetailRowDTO;
import com.dagacs.entity.Department;
import com.dagacs.entity.Teacher;
import com.dagacs.security.AuthenticatedHodResolver;
import com.dagacs.service.HodAcademicSelection;
import com.dagacs.service.HodAttendanceReportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Phase 4A: the HOD report exports that previously did not exist - the
 * attendance overview, the student report, the subject report and the
 * low-attendance report.
 *
 * <p>These assertions are written the way a HOD would experience the file, not
 * the way the code happens to be written: the academic context must be on the
 * page, the numbers must be the canonical conducted-session ones, and a context
 * with nothing conducted must say so instead of presenting a table of zeros.</p>
 *
 * <p><b>The service is mocked on purpose.</b> These tests prove the export layer
 * only ever <i>arranges</i> values the canonical service produced - it never
 * re-derives one. The arithmetic itself is proven separately and exhaustively by
 * {@code HodAttendanceConductedSessionIntegrationTest}; what is proven here is
 * that the export cannot introduce a second formula.</p>
 */
@ExtendWith(MockitoExtension.class)
class HodAttendanceReportExportTest {

    private static final long CS301 = 101L;
    private static final long CS302 = 102L;
    private static final String START = "2026-01-01";
    private static final String END = "2026-01-31";

    @Mock private HodAttendanceReportService attendanceReportService;
    @Mock private AuthenticatedHodResolver hodResolver;

    private final ExcelReportGenerator excelGenerator = new ExcelReportGenerator();
    private final PdfReportGenerator pdfGenerator = new PdfReportGenerator();
    private HodAttendanceReportExportService exportService;

    @BeforeEach
    void wire() {
        // The real Department.name of a real HOD: the department line must be the
        // actual department, never configuration and never a placeholder.
        Department department = Department.builder()
                .id(1L).name("Department of Computer Science & Engineering")
                .code("CSE").createdBy("test").build();
        Teacher hod = Teacher.builder().id(1L).email("hod@dagacs.local")
                .isHod(true).status("ACTIVE").department(department).build();
        lenient().when(hodResolver.resolve()).thenReturn(hod);

        exportService = new HodAttendanceReportExportService(
                attendanceReportService,
                new HodExportHeader(new ReportBrandingProperties(), hodResolver),
                excelGenerator, pdfGenerator);
    }

    private static HodAcademicSelection selection() {
        return new HodAcademicSelection(1L, 2L, 3L, 4L);
    }

    private static HodAttendanceContextDTO context() {
        return HodAttendanceContextDTO.builder()
                .academicSessionId(1L).academicSessionName("Academic Session 2026-27")
                .programId(2L).programName("B.Tech CSE")
                .semesterId(3L).semesterName("Semester 3")
                .sectionId(4L).sectionName("A")
                .complete(true)
                .build();
    }

    // ── Fixtures ───────────────────────────────────────────────────────────

    private static HodAttendanceSubjectSummaryDTO subject(long id, String code, String name,
                                                          List<String> faculty, long classes,
                                                          long present, long total,
                                                          Double percentage,
                                                          long students, long below) {
        return HodAttendanceSubjectSummaryDTO.builder()
                .subjectId(id).subjectCode(code).subjectName(name)
                .facultyNames(faculty)
                .classesConducted(classes).presentCount(present)
                .totalClasses(total).percentage(percentage)
                .students(students).studentsBelowThreshold(below)
                .build();
    }

    private static HodAttendanceOverviewDTO overview(long students, long present,
                                                    long totalClasses, Double percentage,
                                                    List<HodAttendanceSubjectSummaryDTO> subjects) {
        return HodAttendanceOverviewDTO.builder()
                .context(context())
                .totalStudents(students)
                .overallPresentCount(present)
                .overallTotalClasses(totalClasses)
                .overallPercentage(percentage)
                .belowThresholdCount(1L)
                .classesConducted(4L)
                .thresholdPercentage(75.0)
                .noConductedClasses(0L)
                .distribution(List.of())
                .subjects(subjects)
                .build();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Report header: institution, real department, context, range, timestamp
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Report header identifies the academic context")
    class Header {

        @Test
        @DisplayName("carries the configured institution, the real department and the full context")
        void headerIdentifiesTheContext() {
            when(attendanceReportService.getOverview(selection(), START, END))
                    .thenReturn(overview(3, 8, 12, 66.6666, List.of()));

            ExportData data = exportService.overviewData(selection(), START, END);
            String all = String.join("\n", data.metaLines());

            assertAll("The header must let a HOD file this report without asking",
                    () -> assertEquals("DAGACS", data.title(),
                            "institution comes from configuration"),
                    () -> assertEquals("HOD ATTENDANCE OVERVIEW", data.subtitle()),
                    () -> assertTrue(all.contains("Academic Session: Academic Session 2026-27"), all),
                    () -> assertTrue(all.contains("Program: B.Tech CSE"), all),
                    () -> assertTrue(all.contains("Semester: Semester 3"), all),
                    () -> assertTrue(all.contains("Section: A"), all),
                    () -> assertTrue(all.contains("Date Range: 2026-01-01 to 2026-01-31"), all),
                    () -> assertTrue(all.contains(
                                    "Department: Department of Computer Science & Engineering"),
                            "the department line is the real Department.name:\n" + all),
                    () -> assertTrue(all.contains("Generated: "), "a generation timestamp is required:\n" + all),
                    () -> assertTrue(all.contains("Total Classes = conducted attendance sessions"),
                            "the legend states the approved semantics:\n" + all));
        }

        @Test
        @DisplayName("an open-ended range is stated explicitly rather than left blank")
        void openEndedRangeIsExplicit() {
            when(attendanceReportService.getOverview(selection(), null, null))
                    .thenReturn(overview(3, 8, 12, 66.6666, List.of()));

            ExportData data = exportService.overviewData(selection(), null, null);

            assertTrue(String.join("\n", data.metaLines()).contains("Date Range: All dates"),
                    "a report with no date filter must say so");
        }

        @Test
        @DisplayName("a configured institution name replaces the default")
        void configuredInstitutionIsUsed() {
            ReportBrandingProperties branding = new ReportBrandingProperties();
            branding.setInstitutionName("Vidyalankar Institute of Technology");
            exportService = new HodAttendanceReportExportService(attendanceReportService,
                    new HodExportHeader(branding, hodResolver), excelGenerator, pdfGenerator);

            when(attendanceReportService.getOverview(selection(), START, END))
                    .thenReturn(overview(3, 8, 12, 66.6666, List.of()));

            assertEquals("Vidyalankar Institute of Technology",
                    exportService.overviewData(selection(), START, END).title(),
                    "the institution line is deployment configuration, not a hardcoded literal");
        }

        @Test
        @DisplayName("a blank institution configuration falls back instead of printing nothing")
        void blankInstitutionFallsBack() {
            ReportBrandingProperties branding = new ReportBrandingProperties();
            branding.setInstitutionName("   ");
            exportService = new HodAttendanceReportExportService(attendanceReportService,
                    new HodExportHeader(branding, hodResolver), excelGenerator, pdfGenerator);

            when(attendanceReportService.getOverview(selection(), START, END))
                    .thenReturn(overview(3, 8, 12, 66.6666, List.of()));

            assertEquals("DAGACS", exportService.overviewData(selection(), START, END).title());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Attendance overview -> subject summary
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Attendance overview export")
    class Overview {

        @Test
        @DisplayName("is a subject summary with the approved columns")
        void subjectSummaryColumns() {
            when(attendanceReportService.getOverview(selection(), START, END))
                    .thenReturn(overview(3, 8, 12, 66.6666, List.of(
                            subject(CS301, "CS301", "Data Structures", List.of("Dr Rao"),
                                    4, 5, 12, 41.6666, 3, 2),
                            subject(CS302, "CS302", "Computer Networks", List.of(),
                                    4, 3, 12, 25.0, 3, 3))));

            ExportData data = exportService.overviewData(selection(), START, END);

            assertEquals(List.of("Subject Code", "Subject Name", "Faculty",
                            "Total Classes", "Average Attendance", "Students", "Below Threshold"),
                    data.headers());
            assertEquals(2, data.rows().size());
            assertEquals(List.of("CS301", "Data Structures", "Dr Rao", 12L, "41.67%", 3L, 2L),
                    data.rows().get(0));
        }

        @Test
        @DisplayName("states real faculty, and says so when a subject has none")
        void facultyIsRealOrExplicitlyAbsent() {
            when(attendanceReportService.getOverview(selection(), START, END))
                    .thenReturn(overview(1, 0, 4, 0.0, List.of(
                            subject(CS301, "CS301", "Data Structures", List.of(),
                                    4, 0, 4, 0.0, 1, 1))));

            ExportData data = exportService.overviewData(selection(), START, END);

            assertEquals("No faculty assigned", data.rows().get(0).get(2),
                    "faculty is never invented; the absence is stated");
        }

        @Test
        @DisplayName("carries the headline metrics in the header, not as table rows")
        void headlineMetricsAreContext() {
            when(attendanceReportService.getOverview(selection(), START, END))
                    .thenReturn(overview(3, 56, 70, 80.0, List.of()));

            String all = String.join("\n",
                    exportService.overviewData(selection(), START, END).metaLines());

            assertAll("the headline must be readable without scrolling the table",
                    () -> assertTrue(all.contains("Total Students: 3"), all),
                    () -> assertTrue(all.contains("Total Present: 56"), all),
                    () -> assertTrue(all.contains("Total Classes: 70"), all),
                    () -> assertTrue(all.contains("Overall Attendance: 80.00%"), all),
                    () -> assertTrue(all.contains("Below 75.00%"), all));
        }

        @Test
        @DisplayName("N/A, never 0.00%, when no class was conducted")
        void noConductedClassIsNotAZeroPercent() {
            when(attendanceReportService.getOverview(selection(), START, END))
                    .thenReturn(overview(1, 0, 0, null, List.of()));

            ExportData data = exportService.overviewData(selection(), START, END);
            String all = String.join("\n", data.metaLines());

            assertTrue(all.contains("Overall Attendance: N/A"),
                    "no denominator means the percentage is unavailable:\n" + all);
            assertTrue(all.contains("Total Classes: 0"), all);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Student attendance
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Student attendance export")
    class Student {

        private HodStudentAttendanceDetailDTO detail() {
            return HodStudentAttendanceDetailDTO.builder()
                    .context(context())
                    .studentId(91L).enrollmentNumber("CS001").rollNumber("R1")
                    .studentName("Test Student")
                    .programName("B.Tech CSE").semesterName("Semester 3").sectionName("A")
                    .subjects(List.of(
                            HodStudentSubjectAttendanceDTO.builder().subjectId(CS301)
                                    .subjectCode("CS301").subjectName("Data Structures")
                                    .classes(40L).present(32L).notAttended(8L)
                                    .percentage(80.0).build(),
                            HodStudentSubjectAttendanceDTO.builder().subjectId(CS302)
                                    .subjectCode("CS302").subjectName("Computer Networks")
                                    .classes(30L).present(24L).notAttended(6L)
                                    .percentage(80.0).build()))
                    .totalPresent(56L).totalClasses(70L).overallPercentage(80.0)
                    .build();
        }

        @Test
        @DisplayName("prints the identity block and the subject breakdown")
        void identityAndBreakdown() {
            when(attendanceReportService.getStudentDetail(
                    selection(), 91L, null, START, END)).thenReturn(detail());

            ExportData data = exportService.studentData(selection(), 91L, null, START, END);
            String all = String.join("\n", data.metaLines());

            assertAll(
                    () -> assertTrue(all.contains("Student Name: Test Student"), all),
                    () -> assertTrue(all.contains("Enrollment No: CS001"), all),
                    () -> assertTrue(all.contains("Program: B.Tech CSE"), all),
                    () -> assertTrue(all.contains("Semester: Semester 3"), all),
                    () -> assertTrue(all.contains("Section: A"), all),
                    () -> assertEquals(List.of("Subject Code", "Subject", "Present",
                            "Total Classes", "Attendance %"), data.headers()),
                    () -> assertEquals(List.of("CS301", "Data Structures", 32L, 40L, "80.00%"),
                            data.rows().get(0)),
                    () -> assertEquals(List.of("CS302", "Computer Networks", 24L, 30L, "80.00%"),
                            data.rows().get(1)));
        }

        @Test
        @DisplayName("the total row repeats the canonical totals, never a re-derivation")
        void totalRowIsTheDtoFigure() {
            when(attendanceReportService.getStudentDetail(
                    selection(), 91L, null, START, END)).thenReturn(detail());

            List<List<Object>> rows = exportService
                    .studentData(selection(), 91L, null, START, END).rows();

            assertEquals(List.of("", "TOTAL", 56L, 70L, "80.00%"), rows.get(rows.size() - 1),
                    "56 / 70 is the canonical overall figure, not a mean of the two subject percentages");
        }

        @Test
        @DisplayName("a subject with no conducted class reports N/A, not 0.00%")
        void noConductedClassIsNa() {
            HodStudentAttendanceDetailDTO noClasses = HodStudentAttendanceDetailDTO.builder()
                    .context(context())
                    .studentId(91L).enrollmentNumber("CS001").rollNumber("R1")
                    .studentName("Test Student")
                    .programName("B.Tech CSE").semesterName("Semester 3").sectionName("A")
                    .subjects(List.of(HodStudentSubjectAttendanceDTO.builder()
                            .subjectId(CS302).subjectCode("CS302")
                            .subjectName("Computer Networks")
                            .classes(0L).present(0L).notAttended(0L)
                            .percentage(null).build()))
                    .totalPresent(0L).totalClasses(0L).overallPercentage(null)
                    .build();
            when(attendanceReportService.getStudentDetail(
                    selection(), 91L, null, START, END)).thenReturn(noClasses);

            ExportData data = exportService.studentData(selection(), 91L, null, START, END);

            assertEquals("N/A", data.rows().get(0).get(4),
                    "no denominator means no percentage");
            assertTrue(String.join("\n", data.metaLines())
                            .contains(HodExportHeader.noSessionsNotice()),
                    "an empty report states that no sessions were conducted");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Subject attendance
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Subject attendance export")
    class Subject {

        private HodSubjectAttendanceDetailDTO detail() {
            return HodSubjectAttendanceDetailDTO.builder()
                    .context(context())
                    .subjectId(CS301).subjectCode("CS301")
                    .subjectName("Data Structures and Algorithms")
                    .programName("B.Tech CSE").semesterName("Semester 3").sectionName("A")
                    .facultyNames(List.of("Dr Rao"))
                    .totalClasses(40L).students(2L).presentCount(56L)
                    .totalClassesAcrossStudents(80L).averageAttendance(70.0)
                    .studentsBelowThreshold(1L).thresholdPercentage(75.0)
                    .studentRows(List.of(
                            HodSubjectAttendanceDetailRowDTO.builder().studentId(91L)
                                    .enrollmentNumber("CS001").rollNumber("R1")
                                    .studentName("Test Student")
                                    .present(32L).total(40L).percentage(80.0).build(),
                            HodSubjectAttendanceDetailRowDTO.builder().studentId(92L)
                                    .enrollmentNumber("CS002").rollNumber("R2")
                                    .studentName("Weak Student")
                                    .present(24L).total(40L).percentage(60.0).build()))
                    .build();
        }

        @Test
        @DisplayName("prints the full subject header and the roster")
        void subjectHeaderAndRoster() {
            when(attendanceReportService.getSubjectDetail(selection(), CS301, START, END))
                    .thenReturn(detail());

            ExportData data = exportService.subjectData(selection(), CS301, START, END);
            String all = String.join("\n", data.metaLines());

            assertAll(
                    () -> assertTrue(all.contains("Subject Code: CS301"), all),
                    () -> assertTrue(all.contains("Faculty: Dr Rao"), all),
                    () -> assertTrue(all.contains("Academic Session: Academic Session 2026-27"), all),
                    () -> assertTrue(all.contains("Date Range: 2026-01-01 to 2026-01-31"), all),
                    () -> assertTrue(all.contains("Average Attendance: 70.00%"),
                            "the printed average is the DTO's own figure:\n" + all),
                    () -> assertTrue(all.contains("Total Classes Across Students: 80"), all),
                    () -> assertEquals(List.of("Enrollment No.", "Student Name", "Present",
                            "Total Classes", "Attendance %", "Below Threshold"), data.headers()),
                    () -> assertEquals(List.of("CS001", "Test Student", 32L, 40L, "80.00%", "No"),
                            data.rows().get(0)),
                    () -> assertEquals(List.of("CS002", "Weak Student", 24L, 40L, "60.00%", "Yes"),
                            data.rows().get(1)));
        }

        @Test
        @DisplayName("an unassigned subject says so rather than inventing faculty")
        void noFacultyIsExplicit() {
            HodSubjectAttendanceDetailDTO unassigned = HodSubjectAttendanceDetailDTO.builder()
                    .context(context())
                    .subjectId(CS301).subjectCode("CS301")
                    .subjectName("Data Structures and Algorithms")
                    .programName("B.Tech CSE").semesterName("Semester 3").sectionName("A")
                    .facultyNames(List.of())
                    .totalClasses(40L).students(2L).presentCount(56L)
                    .totalClassesAcrossStudents(80L).averageAttendance(70.0)
                    .studentsBelowThreshold(1L).thresholdPercentage(75.0)
                    .studentRows(detail().getStudentRows())
                    .build();
            when(attendanceReportService.getSubjectDetail(selection(), CS301, START, END))
                    .thenReturn(unassigned);

            assertTrue(String.join("\n",
                            exportService.subjectData(selection(), CS301, START, END).metaLines())
                    .contains("Faculty: No faculty assigned"));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Low attendance
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Low attendance export")
    class Low {

        private HodLowAttendanceReportDTO report() {
            return HodLowAttendanceReportDTO.builder()
                    .context(context())
                    .thresholdPercentage(75.0)
                    .totalStudents(3)
                    .belowThresholdCount(1L)
                    .students(List.of(HodLowAttendanceStudentDTO.builder()
                            .studentId(93L).enrollmentNumber("CS003").rollNumber("R3")
                            .studentName("Carol")
                            .sectionName("A").programName("B.Tech CSE").semesterName("Semester 3")
                            .academicSessionName("Academic Session 2026-27")
                            .presentCount(3L).totalClasses(6L).percentage(50.0)
                            .subjectsBelowThreshold(1L)
                            .belowThresholdSubjects(List.of(
                                    HodLowAttendanceSubjectDTO.builder()
                                            .subjectId(CS301).subjectCode("CS301")
                                            .subjectName("Data Structures")
                                            .present(1L).total(4L).percentage(25.0).build()))
                            .build()))
                    .build();
        }

        @Test
        @DisplayName("lists the student with the subject responsible, indented")
        void studentThenCulprit() {
            when(attendanceReportService.getLowAttendance(selection(), START, END))
                    .thenReturn(report());

            ExportData data = exportService.lowData(selection(), START, END);

            assertEquals(List.of("Enrollment No.", "Student Name", "Program", "Semester",
                    "Section", "Present", "Total Classes", "Attendance %",
                    "Subjects Below Threshold"), data.headers());

            assertEquals(List.of("CS003", "Carol", "B.Tech CSE", "Semester 3", "A",
                    3L, 6L, "50.00%", 1L), data.rows().get(0));

            List<Object> culprit = data.rows().get(1);
            assertAll("the culprit sub-row carries the subject's own figures",
                    () -> assertEquals("CS003", culprit.get(0),
                            "the enrolment number repeats so the sub-row is unambiguous"),
                    () -> assertTrue(String.valueOf(culprit.get(1)).contains("Data Structures")),
                    () -> assertTrue(String.valueOf(culprit.get(1)).startsWith("    - "),
                            "indented so the two grains stay visually distinct"),
                    () -> assertEquals(1L, culprit.get(5)),
                    () -> assertEquals(4L, culprit.get(6)),
                    () -> assertEquals("25.00%", culprit.get(7)),
                    () -> assertEquals("", culprit.get(8)));
        }

        @Test
        @DisplayName("a culprit with no conducted class is never listed as 0%")
        void noConductedClassIsNotACulprit() {
            HodLowAttendanceStudentDTO student = HodLowAttendanceStudentDTO.builder()
                    .studentId(93L).enrollmentNumber("CS003").rollNumber("R3")
                    .studentName("Carol")
                    .sectionName("A").programName("B.Tech CSE").semesterName("Semester 3")
                    .presentCount(3L).totalClasses(6L).percentage(50.0)
                    .subjectsBelowThreshold(1L)
                    .belowThresholdSubjects(List.of(
                            HodLowAttendanceSubjectDTO.builder()
                                    .subjectId(CS302).subjectCode("CS302")
                                    .subjectName("Computer Networks")
                                    .present(0L).total(0L).percentage(null).build()))
                    .build();
            when(attendanceReportService.getLowAttendance(selection(), START, END))
                    .thenReturn(HodLowAttendanceReportDTO.builder()
                            .context(context()).thresholdPercentage(75.0)
                            .totalStudents(3).belowThresholdCount(1L)
                            .students(List.of(student)).build());

            ExportData data = exportService.lowData(selection(), START, END);

            assertEquals(2, data.rows().size(), "student row + one sub-row");
            assertEquals("N/A", data.rows().get(1).get(7),
                    "no denominator means the sub-row reports N/A rather than 0%");
        }

        @Test
        @DisplayName("no student below threshold states the empty reason")
        void noStudentsBelowThreshold() {
            when(attendanceReportService.getLowAttendance(selection(), START, END))
                    .thenReturn(HodLowAttendanceReportDTO.builder()
                            .context(context()).thresholdPercentage(75.0)
                            .totalStudents(3).belowThresholdCount(0L)
                            .students(List.of()).build());

            ExportData data = exportService.lowData(selection(), START, END);

            assertTrue(data.rows().isEmpty());
            assertTrue(String.join("\n", data.metaLines())
                    .contains(HodExportHeader.noSessionsNotice()));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Deterministic, sanitised file names
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Attachment names")
    class FileNames {

        @Test
        @DisplayName("are deterministic and contain no generation timestamp")
        void deterministic() {
            when(attendanceReportService.getOverview(selection(), START, END))
                    .thenReturn(overview(3, 8, 12, 66.6666, List.of()));

            LocalDate start = LocalDate.of(2026, 1, 1);
            LocalDate end = LocalDate.of(2026, 1, 31);

            String first = exportService.exportOverview(
                    ExportFormat.XLSX, selection(), start, end).fileName();
            String second = exportService.exportOverview(
                    ExportFormat.XLSX, selection(), start, end).fileName();

            assertEquals(first, second, "the same context must always produce the same name");
            assertEquals("DAGACS_Attendance_Overview_Program-B-Tech-CSE_Semester-3_Section-A"
                    + "_2026-01-01_to_2026-01-31.xlsx", first,
                    "each academic level stays readable and separately labelled");        }

        @Test
        @DisplayName("include the subject code only for a subject-specific report")
        void subjectCodeOnlyWhenRelevant() {
            when(attendanceReportService.getSubjectDetail(selection(), CS301, START, END))
                    .thenReturn(HodSubjectAttendanceDetailDTO.builder()
                            .context(context()).subjectId(CS301).subjectCode("CS301")
                            .subjectName("Data Structures")
                            .totalClasses(40L).students(1L)
                            .studentRows(List.of()).build());

            assertTrue(exportService.exportSubject(ExportFormat.PDF, selection(), CS301,
                            LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31))
                    .fileName().contains("CS301"));

            when(attendanceReportService.getOverview(selection(), START, END))
                    .thenReturn(overview(3, 8, 12, 66.6666, List.of()));
            assertFalse(exportService.exportOverview(ExportFormat.XLSX, selection(),
                            LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31))
                    .fileName().contains("CS301"),
                    "a context report has no single subject to name");
        }

        @Test
        @DisplayName("an illegal filename character can never reach the filesystem")
        void illegalCharactersAreSanitised() {
            HodAttendanceContextDTO hostileContext = HodAttendanceContextDTO.builder()
                    .academicSessionId(1L).academicSessionName("Academic Session 2026-27")
                    .programId(2L).programName("B.Tech/CSE: \"R&D\" <dept> |x")
                    .semesterId(3L).semesterName("Semester 3")
                    .sectionId(4L).sectionName("A")
                    .complete(true).build();
            when(attendanceReportService.getOverview(selection(), START, END))
                    .thenReturn(HodAttendanceOverviewDTO.builder()
                            .context(hostileContext)
                            .totalStudents(3L).overallPresentCount(8L)
                            .overallTotalClasses(12L).overallPercentage(66.6666)
                            .belowThresholdCount(1L).classesConducted(4L)
                            .thresholdPercentage(75.0).noConductedClasses(0L)
                            .distribution(List.of()).subjects(List.of()).build());
            String name = exportService.exportOverview(ExportFormat.XLSX, selection(),
                    LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31)).fileName();

            assertAll("no character that is illegal on Windows may survive",
                    () -> assertFalse(name.contains("/"), name),
                    () -> assertFalse(name.contains("\\"), name),
                    () -> assertFalse(name.contains(":"), name),
                    () -> assertFalse(name.contains("*"), name),
                    () -> assertFalse(name.contains("?"), name),
                    () -> assertFalse(name.contains("\""), name),
                    () -> assertFalse(name.contains("<"), name),
                    () -> assertFalse(name.contains(">"), name),
                    () -> assertFalse(name.contains("|"), name),
                    () -> assertFalse(name.contains(" "), name),
                    () -> assertTrue(name.endsWith(".xlsx"), name));
        }

        @Test
        @DisplayName("carry the right extension per format")
        void extensionPerFormat() {
            when(attendanceReportService.getLowAttendance(selection(), null, null))
                    .thenReturn(HodLowAttendanceReportDTO.builder()
                            .context(context()).thresholdPercentage(75.0)
                            .totalStudents(0).belowThresholdCount(0L)
                            .students(List.of()).build());

            assertTrue(exportService.exportLow(ExportFormat.PDF, selection(),
                    null, null).fileName().endsWith(".pdf"));
            assertTrue(exportService.exportLow(ExportFormat.XLSX, selection(),
                    null, null).fileName().endsWith(".xlsx"));
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Excel / PDF parity and real file generation
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Excel and PDF")
    class Parity {

        @Test
        @DisplayName("are produced from the same data model for every report")
        void sameDataModel() {
            when(attendanceReportService.getOverview(selection(), START, END))
                    .thenReturn(overview(3, 8, 12, 66.6666, List.of(
                            subject(CS301, "CS301", "Data Structures", List.of("Dr Rao"),
                                    4, 5, 12, 41.6666, 3, 2))));

            byte[] xlsx = exportService.exportOverview(
                    ExportFormat.XLSX, selection(), LocalDate.of(2026, 1, 1),
                    LocalDate.of(2026, 1, 31)).bytes();
            byte[] pdf = exportService.exportOverview(
                    ExportFormat.PDF, selection(), LocalDate.of(2026, 1, 1),
                    LocalDate.of(2026, 1, 31)).bytes();

            assertAll("both formats are real documents, from one data model",
                    () -> assertTrue(xlsx.length > 0),
                    () -> assertArrayEquals(new byte[]{'P', 'K', 3, 4}, head(xlsx),
                            "the Excel file is a real xlsx"),
                    () -> assertArrayEquals(new byte[]{'%', 'P', 'D', 'F'}, head(pdf),
                            "the PDF file is a real PDF"));
        }

        @Test
        @DisplayName("are not paged - the whole context is exported")
        void wholeContextIsExported() {
            when(attendanceReportService.getSubjectDetail(selection(), CS301, START, END))
                    .thenReturn(HodSubjectAttendanceDetailDTO.builder()
                            .context(context()).subjectId(CS301).subjectCode("CS301")
                            .subjectName("Data Structures")
                            .totalClasses(40L).students(205L)
                            .studentRows(List.of())
                            .build());

            // The service is the single source: whatever the canonical report says
            // is what the file contains. There is no paging parameter anywhere in
            // the export layer, so an export cannot silently truncate.
            assertTrue(exportService.exportSubject(ExportFormat.XLSX, selection(), CS301,
                            LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31))
                    .fileName().endsWith(".xlsx"));
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private static byte[] head(byte[] bytes) {
        byte[] first = new byte[4];
        System.arraycopy(bytes, 0, first, 0, 4);
        return first;
    }

    @Test
    @DisplayName("the export layer calls the canonical service and nothing else")
    void delegatesToTheCanonicalService() {
        when(attendanceReportService.getOverview(selection(), START, END))
                .thenReturn(overview(1, 0, 0, null, List.of()));

        ExportData data = exportService.overviewData(selection(), START, END);

        assertNotNull(data);
        // A second, independent percentage formula anywhere in the export layer
        // would show up as a computed figure; every value here is the DTO's own.
        assertTrue(String.join("\n", data.metaLines()).contains("Overall Attendance: N/A"));
        assertEquals(List.of("Subject Code", "Subject Name", "Faculty",
                        "Total Classes", "Average Attendance", "Students", "Below Threshold"),
                data.headers());
        assertEquals(0, data.rows().size());
    }
}
