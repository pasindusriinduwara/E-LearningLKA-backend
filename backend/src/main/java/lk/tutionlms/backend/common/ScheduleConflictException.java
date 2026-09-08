package lk.tutionlms.backend.common;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class ScheduleConflictException extends RuntimeException {

    private final String conflictDetails;

    public ScheduleConflictException(String message) {
        super(message);
        this.conflictDetails = message;
    }

    public ScheduleConflictException(String message, String conflictDetails) {
        super(message);
        this.conflictDetails = conflictDetails;
    }

    public String getConflictDetails() {
        return conflictDetails;
    }
}
