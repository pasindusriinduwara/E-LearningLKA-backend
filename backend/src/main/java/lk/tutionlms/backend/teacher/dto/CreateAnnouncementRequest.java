package lk.tutionlms.backend.teacher.dto;
import java.util.UUID;
public record CreateAnnouncementRequest(UUID batchId, String title, String description, String type, String time) {}
