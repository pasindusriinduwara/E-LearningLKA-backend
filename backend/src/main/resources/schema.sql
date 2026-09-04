-- PostgreSQL Schema generated from pgAdmin 4 ERD tool
-- Tuition LMS Database Schema

BEGIN;

CREATE TABLE IF NOT EXISTS public.users
(
    id uuid NOT NULL DEFAULT gen_random_uuid(),
    created_at timestamp without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp without time zone,
    is_deleted boolean NOT NULL DEFAULT false,
    email character varying(150) COLLATE pg_catalog."default" NOT NULL,
    password_hash character varying(255) COLLATE pg_catalog."default" NOT NULL,
    phone_number character varying(20) COLLATE pg_catalog."default",
    status character varying(20) COLLATE pg_catalog."default" NOT NULL DEFAULT 'ACTIVE'::character varying,
    user_type character varying(30) COLLATE pg_catalog."default" NOT NULL,
    CONSTRAINT users_pkey PRIMARY KEY (id),
    CONSTRAINT users_email_key UNIQUE (email),
    CONSTRAINT users_phone_number_key UNIQUE (phone_number)
);

CREATE TABLE IF NOT EXISTS public.institutes
(
    id uuid NOT NULL DEFAULT gen_random_uuid(),
    created_at timestamp without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp without time zone,
    is_deleted boolean NOT NULL DEFAULT false,
    name character varying(150) COLLATE pg_catalog."default" NOT NULL,
    code character varying(50) COLLATE pg_catalog."default" NOT NULL,
    contact_number character varying(30) COLLATE pg_catalog."default",
    email character varying(100) COLLATE pg_catalog."default",
    logo_url character varying(500) COLLATE pg_catalog."default",
    CONSTRAINT institutes_pkey PRIMARY KEY (id),
    CONSTRAINT institutes_code_key UNIQUE (code)
);

CREATE TABLE IF NOT EXISTS public.branches
(
    id uuid NOT NULL DEFAULT gen_random_uuid(),
    created_at timestamp without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp without time zone,
    is_deleted boolean NOT NULL DEFAULT false,
    institute_id uuid NOT NULL,
    name character varying(150) COLLATE pg_catalog."default" NOT NULL,
    city character varying(100) COLLATE pg_catalog."default" NOT NULL,
    address text COLLATE pg_catalog."default",
    CONSTRAINT branches_pkey PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS public.class_rooms
(
    id uuid NOT NULL DEFAULT gen_random_uuid(),
    created_at timestamp without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp without time zone,
    is_deleted boolean NOT NULL DEFAULT false,
    branch_id uuid NOT NULL,
    name character varying(100) COLLATE pg_catalog."default" NOT NULL,
    capacity integer DEFAULT 100,
    CONSTRAINT class_rooms_pkey PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS public.subjects
(
    id uuid NOT NULL DEFAULT gen_random_uuid(),
    created_at timestamp without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp without time zone,
    is_deleted boolean NOT NULL DEFAULT false,
    name character varying(150) COLLATE pg_catalog."default" NOT NULL,
    code character varying(20) COLLATE pg_catalog."default" NOT NULL,
    active boolean NOT NULL DEFAULT true,
    CONSTRAINT subjects_pkey PRIMARY KEY (id),
    CONSTRAINT subjects_code_key UNIQUE (code)
);

CREATE TABLE IF NOT EXISTS public.teachers
(
    id uuid NOT NULL DEFAULT gen_random_uuid(),
    created_at timestamp without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp without time zone,
    is_deleted boolean NOT NULL DEFAULT false,
    user_id uuid,
    title character varying(255) COLLATE pg_catalog."default" DEFAULT 'Mr.'::character varying,
    name character varying(255) COLLATE pg_catalog."default" NOT NULL,
    qualification character varying(255) COLLATE pg_catalog."default",
    bio text COLLATE pg_catalog."default",
    photo_url character varying(500) COLLATE pg_catalog."default",
    CONSTRAINT teachers_pkey PRIMARY KEY (id),
    CONSTRAINT teachers_user_id_key UNIQUE (user_id)
);

CREATE TABLE IF NOT EXISTS public.students
(
    id uuid NOT NULL DEFAULT gen_random_uuid(),
    created_at timestamp without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp without time zone,
    is_deleted boolean NOT NULL DEFAULT false,
    user_id uuid,
    student_id character varying(255) COLLATE pg_catalog."default" NOT NULL,
    name character varying(255) COLLATE pg_catalog."default" NOT NULL,
    initials character varying(255) COLLATE pg_catalog."default",
    exam character varying(255) COLLATE pg_catalog."default",
    stream character varying(255) COLLATE pg_catalog."default",
    medium character varying(255) COLLATE pg_catalog."default",
    barcode_qr character varying(100) COLLATE pg_catalog."default",
    address text COLLATE pg_catalog."default",
    date_of_birth date,
    CONSTRAINT students_pkey PRIMARY KEY (id),
    CONSTRAINT students_barcode_qr_key UNIQUE (barcode_qr),
    CONSTRAINT students_student_id_key UNIQUE (student_id),
    CONSTRAINT students_user_id_key UNIQUE (user_id)
);

CREATE TABLE IF NOT EXISTS public.parents
(
    id uuid NOT NULL DEFAULT gen_random_uuid(),
    created_at timestamp without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp without time zone,
    is_deleted boolean NOT NULL DEFAULT false,
    user_id uuid,
    name character varying(200) COLLATE pg_catalog."default" NOT NULL,
    phone_number character varying(20) COLLATE pg_catalog."default" NOT NULL,
    relationship character varying(30) COLLATE pg_catalog."default",
    CONSTRAINT parents_pkey PRIMARY KEY (id),
    CONSTRAINT parents_user_id_key UNIQUE (user_id)
);

CREATE TABLE IF NOT EXISTS public.student_parents
(
    student_id uuid NOT NULL,
    parent_id uuid NOT NULL,
    CONSTRAINT student_parents_pkey PRIMARY KEY (student_id, parent_id)
);

CREATE TABLE IF NOT EXISTS public.batches
(
    id uuid NOT NULL DEFAULT gen_random_uuid(),
    created_at timestamp without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp without time zone,
    is_deleted boolean NOT NULL DEFAULT false,
    institute_id uuid,
    subject_id uuid NOT NULL,
    teacher_id uuid NOT NULL,
    name character varying(255) COLLATE pg_catalog."default" NOT NULL,
    exam_year character varying(255) COLLATE pg_catalog."default" NOT NULL,
    monthly_fee numeric(38, 2) NOT NULL DEFAULT 0.00,
    delivery_mode character varying(255) COLLATE pg_catalog."default" NOT NULL DEFAULT 'HYBRID'::character varying,
    is_active boolean NOT NULL DEFAULT true,
    CONSTRAINT batches_pkey PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS public.enrollment_requests
(
    id uuid NOT NULL DEFAULT gen_random_uuid(),
    created_at timestamp without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp without time zone,
    is_deleted boolean NOT NULL DEFAULT false,
    student_id uuid NOT NULL,
    batch_id uuid NOT NULL,
    status character varying(255) COLLATE pg_catalog."default" NOT NULL DEFAULT 'PENDING'::character varying,
    CONSTRAINT enrollment_requests_pkey PRIMARY KEY (id),
    CONSTRAINT unique_student_batch_request UNIQUE (student_id, batch_id)
);

CREATE TABLE IF NOT EXISTS public.enrollments
(
    id uuid NOT NULL DEFAULT gen_random_uuid(),
    created_at timestamp without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp without time zone,
    is_deleted boolean NOT NULL DEFAULT false,
    student_id uuid NOT NULL,
    batch_id uuid NOT NULL,
    enrolled_date date NOT NULL DEFAULT CURRENT_DATE,
    status character varying(255) COLLATE pg_catalog."default" NOT NULL DEFAULT 'ACTIVE'::character varying,
    CONSTRAINT enrollments_pkey PRIMARY KEY (id),
    CONSTRAINT unique_student_batch UNIQUE (student_id, batch_id)
);

CREATE TABLE IF NOT EXISTS public.class_schedules
(
    id uuid NOT NULL DEFAULT gen_random_uuid(),
    created_at timestamp without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp without time zone,
    is_deleted boolean NOT NULL DEFAULT false,
    batch_id uuid NOT NULL,
    class_room_id uuid,
    day_of_week character varying(255) COLLATE pg_catalog."default",
    start_time time without time zone,
    end_time time without time zone,
    accent character varying(255) COLLATE pg_catalog."default",
    date_text character varying(255) COLLATE pg_catalog."default",
    location character varying(255) COLLATE pg_catalog."default",
    delivery_mode character varying(255) COLLATE pg_catalog."default",
    subject character varying(255) COLLATE pg_catalog."default",
    teacher character varying(255) COLLATE pg_catalog."default",
    time_text character varying(255) COLLATE pg_catalog."default",
    title character varying(255) COLLATE pg_catalog."default",
    CONSTRAINT class_schedules_pkey PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS public.class_sessions
(
    id uuid NOT NULL DEFAULT gen_random_uuid(),
    created_at timestamp without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp without time zone,
    is_deleted boolean NOT NULL DEFAULT false,
    batch_id uuid NOT NULL,
    schedule_id uuid,
    session_date date NOT NULL,
    topic_title character varying(200) COLLATE pg_catalog."default",
    live_meeting_url character varying(500) COLLATE pg_catalog."default",
    status character varying(20) COLLATE pg_catalog."default" NOT NULL DEFAULT 'SCHEDULED'::character varying,
    CONSTRAINT class_sessions_pkey PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS public.attendances
(
    id uuid NOT NULL DEFAULT gen_random_uuid(),
    created_at timestamp without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp without time zone,
    is_deleted boolean NOT NULL DEFAULT false,
    session_id uuid NOT NULL,
    student_id uuid NOT NULL,
    check_in_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    attendance_mode character varying(30) COLLATE pg_catalog."default" DEFAULT 'PHYSICAL_SCAN'::character varying,
    status character varying(20) COLLATE pg_catalog."default" NOT NULL DEFAULT 'PRESENT'::character varying,
    CONSTRAINT attendances_pkey PRIMARY KEY (id),
    CONSTRAINT unique_session_student UNIQUE (session_id, student_id)
);

CREATE TABLE IF NOT EXISTS public.lesson_units
(
    id uuid NOT NULL DEFAULT gen_random_uuid(),
    created_at timestamp without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp without time zone,
    is_deleted boolean NOT NULL DEFAULT false,
    batch_id uuid NOT NULL,
    title character varying(150) COLLATE pg_catalog."default" NOT NULL,
    display_order integer DEFAULT 1,
    CONSTRAINT lesson_units_pkey PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS public.learning_materials
(
    id uuid NOT NULL DEFAULT gen_random_uuid(),
    created_at timestamp without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp without time zone,
    is_deleted boolean NOT NULL DEFAULT false,
    batch_id uuid NOT NULL,
    lesson_unit_id uuid,
    title character varying(255) COLLATE pg_catalog."default" NOT NULL,
    resource_type character varying(50) COLLATE pg_catalog."default" NOT NULL,
    file_url character varying(1000) COLLATE pg_catalog."default",
    size_text character varying(255) COLLATE pg_catalog."default",
    time_text character varying(255) COLLATE pg_catalog."default",
    is_free_preview boolean DEFAULT false,
    subject character varying(255) COLLATE pg_catalog."default",
    cloudinary_public_id character varying(255) COLLATE pg_catalog."default",
    CONSTRAINT learning_materials_pkey PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS public.assessments
(
    id uuid NOT NULL DEFAULT gen_random_uuid(),
    created_at timestamp without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp without time zone,
    is_deleted boolean NOT NULL DEFAULT false,
    batch_id uuid NOT NULL,
    title character varying(200) COLLATE pg_catalog."default" NOT NULL,
    assessment_type character varying(30) COLLATE pg_catalog."default" NOT NULL DEFAULT 'MCQ_QUIZ'::character varying,
    total_marks numeric(5, 2) DEFAULT 100.00,
    due_date timestamp without time zone,
    duration_minutes integer DEFAULT 60,
    CONSTRAINT assessments_pkey PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS public.questions
(
    id uuid NOT NULL DEFAULT gen_random_uuid(),
    created_at timestamp without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp without time zone,
    is_deleted boolean NOT NULL DEFAULT false,
    assessment_id uuid NOT NULL,
    question_text text COLLATE pg_catalog."default" NOT NULL,
    image_url character varying(500) COLLATE pg_catalog."default",
    marks numeric(4, 2) DEFAULT 1.00,
    display_order integer DEFAULT 1,
    CONSTRAINT questions_pkey PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS public.question_options
(
    id uuid NOT NULL DEFAULT gen_random_uuid(),
    question_id uuid NOT NULL,
    option_text text COLLATE pg_catalog."default" NOT NULL,
    is_correct boolean NOT NULL DEFAULT false,
    CONSTRAINT question_options_pkey PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS public.submissions
(
    id uuid NOT NULL DEFAULT gen_random_uuid(),
    created_at timestamp without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp without time zone,
    is_deleted boolean NOT NULL DEFAULT false,
    assessment_id uuid NOT NULL,
    student_id uuid NOT NULL,
    submitted_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    score_obtained numeric(5, 2),
    paper_upload_url character varying(500) COLLATE pg_catalog."default",
    feedback text COLLATE pg_catalog."default",
    status character varying(20) COLLATE pg_catalog."default" NOT NULL DEFAULT 'SUBMITTED'::character varying,
    CONSTRAINT submissions_pkey PRIMARY KEY (id),
    CONSTRAINT unique_assessment_student UNIQUE (assessment_id, student_id)
);

CREATE TABLE IF NOT EXISTS public.invoices
(
    id uuid NOT NULL DEFAULT gen_random_uuid(),
    created_at timestamp without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp without time zone,
    is_deleted boolean NOT NULL DEFAULT false,
    student_id uuid NOT NULL,
    batch_id uuid NOT NULL,
    invoice_number character varying(255) COLLATE pg_catalog."default" NOT NULL,
    month character varying(255) COLLATE pg_catalog."default" NOT NULL,
    batch_name character varying(255) COLLATE pg_catalog."default" NOT NULL,
    amount character varying(255) COLLATE pg_catalog."default" NOT NULL,
    due_date date,
    status character varying(255) COLLATE pg_catalog."default" NOT NULL DEFAULT 'Due'::character varying,
    CONSTRAINT invoices_pkey PRIMARY KEY (id),
    CONSTRAINT invoices_invoice_number_key UNIQUE (invoice_number)
);

CREATE TABLE IF NOT EXISTS public.payments
(
    id uuid NOT NULL DEFAULT gen_random_uuid(),
    created_at timestamp without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp without time zone,
    is_deleted boolean NOT NULL DEFAULT false,
    invoice_id uuid NOT NULL,
    amount_paid numeric(10, 2) NOT NULL,
    payment_method character varying(30) COLLATE pg_catalog."default" NOT NULL,
    transaction_ref character varying(100) COLLATE pg_catalog."default",
    slip_image_url character varying(500) COLLATE pg_catalog."default",
    is_verified boolean DEFAULT false,
    paid_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT payments_pkey PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS public.announcements
(
    id uuid NOT NULL DEFAULT gen_random_uuid(),
    created_at timestamp without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp without time zone,
    is_deleted boolean NOT NULL DEFAULT false,
    title character varying(255) COLLATE pg_catalog."default" NOT NULL,
    description character varying(255) COLLATE pg_catalog."default",
    announcement_type character varying(255) COLLATE pg_catalog."default" DEFAULT 'IMPORTANT'::character varying,
    time_text character varying(255) COLLATE pg_catalog."default" DEFAULT '1 hour ago'::character varying,
    batch_id uuid,
    CONSTRAINT announcements_pkey PRIMARY KEY (id)
);

COMMIT;
