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
and scoped to the teacher's batches. Students take quizzes through `/{id}/take`.

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
