# E-LearningLKA-backend

## Cloudinary material upload

Set the Cloudinary credentials in the same PowerShell session used to start the Backend:

```powershell
$env:CLOUDINARY_CLOUD_NAME = "your_cloudinary_cloud_name"
$env:CLOUDINARY_API_KEY = "your_cloudinary_api_key"
$env:CLOUDINARY_API_SECRET = "your_cloudinary_api_secret"
```

All three values are required. The backend rejects startup when a value is missing or
blank; no embedded account or credential fallback is used. An ignored `backend/.env`
file may also supply these values. Never commit that file or real credentials.

Rotate the Cloudinary API credentials previously committed to this repository and update
the deployment secret store / local `.env` with the replacement values. Removing literals
from the current source does not revoke credentials retained in Git history.

The teacher endpoint is:

```text
POST /api/v1/teacher/materials/upload
```

Send an authenticated `multipart/form-data` request with `batchId`, `title`, optional `subject`, and `file`. The Backend verifies that the batch belongs to the logged-in teacher, uploads the file to Cloudinary, and stores the Cloudinary URL and public ID in PostgreSQL.

## Assessment uploads and access

Teacher question papers use `POST /api/v1/assessments/upload-paper`. Student answer files use
`POST /api/v1/assessments/{id}/upload-answer`, with a multipart `file` field. The student route
requires active enrollment and an open, visible assessment that accepts file submissions;
graded submissions cannot be replaced. Full assessment management reads are teacher-only
and scoped to the teacher's batches. Students explicitly start/resume MCQ attempts
through `POST /api/v1/assessments/{id}/start`; `/{id}/take` is a read-only resume.

Local fallback files require bearer authentication and resource ownership checks. The
`assessment_files` table records upload ownership so teachers can preview unassigned papers.
With the current `ddl-auto=update` configuration Hibernate creates this table. If automatic
DDL is disabled, apply `backend/database/004_assessment_file_ownership.sql` before starting
the updated backend. Existing linked local files remain readable through assessment/submission
ownership checks; unlinked legacy uploads without ownership metadata must be uploaded again.

## Assessment result publication

Teachers use the Results page to select a batch and assessment, review or save grades,
and publish all graded results for that assessment. Ungraded submissions stay private.
Students see only their own published marks, feedback and quiz answer review; submitting
a quiz does not immediately release its automatic grade. Regrading or changing an
assessment's total marks makes affected results private until the teacher publishes again.
Unpublish hides released results while retaining saved grades and answers.

Teacher-only endpoints: `GET /api/v1/results`, `GET /api/v1/results/{id}`,
`POST /api/v1/results/{id}/publish`, and `POST /api/v1/results/{id}/unpublish`.
Every endpoint is restricted to the logged-in teacher's batches.

Restart the backend to create the new submission publication columns with the current
`ddl-auto=update` configuration. If automatic DDL is disabled, apply
`backend/database/005_submission_result_publication.sql` before starting the updated backend.
Existing submissions default to unpublished and must be explicitly released by the teacher.

## Announcement access and account suspension

`GET /api/v1/announcements` requires student or teacher authentication. Students receive
announcements from their active enrollments in nondeleted batches. Teachers receive
announcements from their own nondeleted batches plus global announcements. The student
batch endpoint also requires active enrollment; anonymous callers cannot read announcements.

Bearer-token authentication reloads the account and checks its enabled, lock, account-expiry
and credential-expiry flags on each request. Suspending or deleting an account therefore
blocks access through its previously issued JWTs while the account is disabled.

`JWT_EXPIRATION_MS` controls the lifetime of newly issued tokens (default: `86400000`,
24 hours) and must be positive. Existing tokens retain their original expiration, but
account-status checks apply immediately to subsequent requests using those tokens.

## Server-timed quiz attempts

One persisted attempt per student and assessment records `started_at` and `expires_at`.
The deadline is the earlier of the configured duration or assessment due date, interpreted
in `Asia/Colombo`. Start, resume and submit require the authenticated student to have an
active enrollment. Hidden/deleted assessments, wrong assessment types, unknown questions,
foreign options and duplicate answer entries are rejected. Refreshing cannot restart a
quiz. Final submission has a fixed 15-second transport grace; existing submissions remain
idempotent and their published-result visibility rules still apply.

With automatic DDL disabled, apply `backend/database/006_server_timed_quiz_attempts.sql`.
Existing completed submissions remain available through the student's result endpoint.

## Authentication limits and safe errors

Login and registration enforce bounded in-memory limits for both the direct peer IP and
normalized account email (stored only as hashes). Limit responses use HTTP 429 and
`Retry-After`. The `AUTH_LOGIN_*`, `AUTH_REGISTER_*` and `AUTH_RATE_LIMIT_MAX_ENTRIES`
settings are listed in `backend/.env.example`. Forwarded IP headers are deliberately
ignored. With the Next API proxy, the shared Next server IP is the peer: size IP limits
for that traffic and enforce distributed/client-IP limits at a trusted gateway when
deploying multiple instances. Application limits reset when the backend restarts.

Unexpected server errors return a generic message and reference ID; stack traces stay
in server logs. Do not expose those logs publicly.

## Private answer sheets and announcement authorship

New student answer sheets always use local authenticated storage at
`backend/uploads/assessments` (relative to the backend working directory). Mount this
directory on a persistent private volume and do not expose it through static hosting.
Downloads require ownership/enrollment checks and use attachment, no-store and nosniff
headers. Uploads accept PDF/DOC/DOCX/PNG/JPG/WEBP extensions with matching MIME types.
Arbitrary new external answer URLs are rejected; already linked legacy answer URLs are
retained for compatibility. Existing public Cloudinary assets need migration/removal at
the provider to revoke their public URLs. Teacher question papers may still use Cloudinary.

Global announcements record the authenticated creator. Only that teacher can delete their
global announcement; legacy announcements with no author require administrator maintenance.
Batch announcements retain batch-owner checks. With automatic DDL disabled, apply
`backend/database/007_announcement_author.sql`.
