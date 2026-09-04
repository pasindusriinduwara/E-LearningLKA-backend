package lk.tutionlms.backend.teacher;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import lk.tutionlms.backend.content.LearningMaterial;
import lk.tutionlms.backend.content.MaterialRepository;
import lk.tutionlms.backend.identity.User;
import lk.tutionlms.backend.teacher.dto.MaterialUploadResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class MaterialUploadService {
    private static final long MAX_FILE_SIZE = 20L * 1024 * 1024;
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "application/pdf",
            "video/mp4",
            "image/png",
            "image/jpeg",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation");
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "pdf", "mp4", "png", "jpg", "jpeg", "doc", "docx", "ppt", "pptx");
    private final Cloudinary cloudinary;
    private final MaterialRepository materialRepository;
    private final TeacherService teacherService;

    public MaterialUploadResponse upload(
            User user,
            UUID batchId,
            String title,
            String subject,
            MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A file is required");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "File must be 20 MB or smaller");
        }
        String contentType = normalizeContentType(file.getContentType());
        String extension = getExtension(file.getOriginalFilename());
        boolean contentTypeAllowed = contentType != null && ALLOWED_CONTENT_TYPES.contains(contentType);
        boolean genericContentType = contentType == null || contentType.isBlank()
                || "application/octet-stream".equals(contentType);
        if (!contentTypeAllowed && !(genericContentType && ALLOWED_EXTENSIONS.contains(extension))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Only PDF, MP4, PNG, JPG, DOC, and PPT files are supported");
        }
        if (title == null || title.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Title is required");
        }
        if (title.trim().length() > 255) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Title must be 255 characters or fewer");
        }
        if (subject != null && subject.trim().length() > 255) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Subject must be 255 characters or fewer");
        }
        teacherService.verifyBatchOwnership(user, batchId);

        Map<?, ?> result;
        try {
            result = cloudinary.uploader().upload(
                    file.getBytes(),
                    ObjectUtils.asMap(
                            "resource_type", "auto",
                            "folder", "tuition-lms/materials/" + batchId,
                            "use_filename", true,
                            "unique_filename", true));

        } catch (IOException | RuntimeException ex) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY, "Cloudinary upload failed", ex);
        }

        String url = result.get("secure_url") instanceof String value ? value : null;
        String publicId = result.get("public_id") instanceof String value ? value : null;
        if (url == null || url.isBlank() || publicId == null || publicId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Cloudinary returned an invalid upload response");
        }

        LearningMaterial material;
        try {
            material = materialRepository.save(
                    LearningMaterial.builder()
                            .batchId(batchId)
                            .title(title.trim())
                            .subject(subject == null || subject.isBlank() ? null : subject.trim())
                            .type(resolveResourceType(contentType, extension))
                            .size(formatFileSize(file.getSize()))
                            .time("Uploaded just now")
                            .fileUrl(url)
                            .cloudinaryPublicId(publicId)
                            .build());

            return new MaterialUploadResponse(
                    material.getId(), material.getBatchId(), material.getTitle(),
                    material.getType(), material.getFileUrl());
        } catch (RuntimeException ex) {

            try {
                Object resourceType = result.get("resource_type");
                cloudinary.uploader().destroy(publicId, ObjectUtils.asMap(
                        "resource_type", resourceType instanceof String ? resourceType : "auto",
                        "invalidate", true));
            } catch (Exception ignored) {

            }
            throw ex;
        }
    }

    private String normalizeContentType(String contentType) {
        return contentType == null ? null : contentType.toLowerCase(Locale.ROOT).split(";", 2)[0].trim();
    }

    private String getExtension(String fileName) {
        if (fileName == null)
            return "";
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private String resolveResourceType(String contentType, String extension) {
        if ("video/mp4".equals(contentType) || "mp4".equals(extension))
            return "Video";
        if ("application/pdf".equals(contentType) || "pdf".equals(extension))
            return "PDF";
        if ((contentType != null && contentType.startsWith("image/"))
                || Set.of("png", "jpg", "jpeg").contains(extension))
            return "Image";
        if (Set.of("doc", "docx").contains(extension))
            return "Document";
        if (Set.of("ppt", "pptx").contains(extension))
            return "Presentation";
        return "File";
    }

    private String formatFileSize(long bytes) {
        if (bytes < 1024)
            return bytes + " B";
        if (bytes < 1024 * 1024)
            return String.format(Locale.ROOT, "%.1f KB", bytes / 1024.0);
        return String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024.0));
    }
}
