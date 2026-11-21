# E-LearningLKA-backend

## Cloudinary material upload

Set the Cloudinary credentials in the same PowerShell session used to start the Backend:

```powershell
$env:CLOUDINARY_CLOUD_NAME = "your_cloudinary_cloud_name"
$env:CLOUDINARY_API_KEY = "your_cloudinary_api_key"
$env:CLOUDINARY_API_SECRET = "your_cloudinary_api_secret"
```

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
