package lk.tutionlms.backend.teacher.dto;
import java.util.UUID;
public record CreateMaterialRequest(UUID batchId, String title, String subject, String type, String time, String size, String fileUrl) {}
