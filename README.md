# E-LearningLKA-backend

## Cloudinary material upload

Set the Cloudinary credentials in the same PowerShell session used to start the Backend:

```powershell
$env:CLOUDINARY_CLOUD_NAME = "duwtj2wa7"
$env:CLOUDINARY_API_KEY = "423854672574226"
$env:CLOUDINARY_API_SECRET= "tuVirGvc08f0_AxwrFENCvpdFl8"
```

The teacher endpoint is:

```text
POST /api/v1/teacher/materials/upload
```

Send an authenticated `multipart/form-data` request with `batchId`, `title`, optional `subject`, and `file`. The Backend verifies that the batch belongs to the logged-in teacher, uploads the file to Cloudinary, and stores the Cloudinary URL and public ID in PostgreSQL.
