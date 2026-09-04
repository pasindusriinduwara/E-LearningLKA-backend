package lk.tutionlms.backend.enrollment;

import java.util.UUID;

public interface TeacherStudentView {
    UUID getId();
    String getName();
    String getStudentId();
    String getBatchName();
}
