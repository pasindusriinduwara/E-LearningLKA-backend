-- Fresh database baseline generated from and checked against the application mappings.
-- Existing installations must explicitly baseline at version 1 after applying legacy patches.

    create table announcements (
        is_deleted boolean not null,
        created_at timestamp(6) not null,
        updated_at timestamp(6),
        batch_id uuid,
        created_by uuid,
        id uuid not null,
        announcement_type varchar(255),
        description varchar(255),
        time_text varchar(255),
        title varchar(255) not null,
        primary key (id)
    );

    create table assessment_files (
        answer_sheet boolean not null,
        is_deleted boolean not null,
        created_at timestamp(6) not null,
        updated_at timestamp(6),
        assessment_id uuid,
        id uuid not null,
        uploaded_by uuid not null,
        filename varchar(255) not null unique,
        primary key (id)
    );

    create table assessments (
        duration_minutes integer,
        is_deleted boolean not null,
        is_hidden boolean,
        total_marks numeric(5,2),
        created_at timestamp(6) not null,
        due_date timestamp(6),
        updated_at timestamp(6),
        batch_id uuid not null,
        id uuid not null,
        assessment_type varchar(30),
        submission_type varchar(30),
        title varchar(200) not null,
        attachment_url varchar(500),
        instructions TEXT,
        primary key (id)
    );

    create table batches (
        is_active boolean,
        is_deleted boolean not null,
        monthly_fee numeric(38,2),
        created_at timestamp(6) not null,
        updated_at timestamp(6),
        id uuid not null,
        institute_id uuid,
        subject_id uuid not null,
        teacher_id uuid not null,
        delivery_mode varchar(255),
        exam_year varchar(255) not null,
        name varchar(255) not null,
        primary key (id)
    );

    create table class_schedules (
        effective_date date,
        end_time time(0) not null,
        is_deleted boolean not null,
        start_time time(0) not null,
        created_at timestamp(6) not null,
        updated_at timestamp(6),
        day_of_week varchar(15) not null check ((day_of_week in ('MONDAY','TUESDAY','WEDNESDAY','THURSDAY','FRIDAY','SATURDAY','SUNDAY'))),
        batch_id uuid not null,
        id uuid not null,
        delivery_mode varchar(20) not null check ((delivery_mode in ('IN_PERSON','ONLINE','HYBRID'))),
        recurrence_type varchar(20) not null check ((recurrence_type in ('WEEKLY','BI_WEEKLY','ONCE'))),
        accent varchar(255),
        location varchar(255),
        subject varchar(255),
        teacher varchar(255),
        time_text varchar(255),
        title varchar(255) not null,
        primary key (id)
    );

    create table enrollment_requests (
        is_deleted boolean,
        created_at timestamp(6),
        updated_at timestamp(6),
        batch_id uuid not null,
        id uuid not null,
        student_id uuid not null,
        status varchar(255) not null check ((status in ('PENDING','APPROVED','REJECTED'))),
        primary key (id)
    );

    create table enrollments (
        enrolled_date date,
        is_deleted boolean not null,
        created_at timestamp(6) not null,
        updated_at timestamp(6),
        batch_id uuid not null,
        id uuid not null,
        student_id uuid not null,
        status varchar(255),
        primary key (id)
    );

    create table invoices (
        is_deleted boolean not null,
        created_at timestamp(6) not null,
        updated_at timestamp(6),
        id uuid not null,
        student_id uuid,
        amount varchar(255),
        batch_name varchar(255),
        invoice_number varchar(255),
        month varchar(255),
        status varchar(255),
        primary key (id)
    );

    create table learning_materials (
        is_deleted boolean not null,
        created_at timestamp(6) not null,
        updated_at timestamp(6),
        batch_id uuid not null,
        id uuid not null,
        resource_type varchar(50) not null,
        file_url varchar(1000),
        cloudinary_public_id varchar(255),
        size_text varchar(255),
        subject varchar(255),
        time_text varchar(255),
        title varchar(255) not null,
        primary key (id)
    );

    create table notifications (
        created_at timestamp(6) with time zone not null,
        read_at timestamp(6) with time zone,
        id uuid not null,
        recipient_id uuid not null,
        type varchar(60) not null,
        title varchar(160) not null,
        link varchar(512),
        message varchar(1000) not null,
        primary key (id)
    );

    create table question_options (
        is_correct boolean not null,
        id uuid not null,
        question_id uuid not null,
        option_text TEXT not null,
        primary key (id)
    );

    create table questions (
        display_order integer,
        is_deleted boolean not null,
        marks numeric(4,2),
        created_at timestamp(6) not null,
        updated_at timestamp(6),
        assessment_id uuid not null,
        id uuid not null,
        image_url varchar(500),
        question_text TEXT not null,
        primary key (id)
    );

    create table quiz_attempts (
        is_deleted boolean not null,
        created_at timestamp(6) not null,
        expires_at timestamp(6) with time zone not null,
        started_at timestamp(6) with time zone not null,
        updated_at timestamp(6),
        assessment_id uuid not null,
        id uuid not null,
        student_id uuid not null,
        primary key (id),
        constraint uk_quiz_attempt_student unique (assessment_id, student_id)
    );

    create table students (
        date_of_birth date,
        is_deleted boolean not null,
        created_at timestamp(6) not null,
        updated_at timestamp(6),
        id uuid not null,
        user_id uuid unique,
        exam varchar(255),
        initials varchar(255),
        medium varchar(255),
        name varchar(255) not null,
        stream varchar(255),
        student_id varchar(255) not null unique,
        primary key (id)
    );

    create table subjects (
        active boolean not null,
        is_deleted boolean not null,
        created_at timestamp(6) not null,
        updated_at timestamp(6),
        id uuid not null,
        name varchar(150) not null unique,
        primary key (id)
    );

    create table submission_answers (
        is_correct boolean not null,
        is_deleted boolean not null,
        marks_awarded numeric(5,2),
        created_at timestamp(6) not null,
        updated_at timestamp(6),
        id uuid not null,
        question_id uuid not null,
        selected_option_id uuid,
        submission_id uuid not null,
        answer_text TEXT,
        primary key (id)
    );

    create table submissions (
        is_deleted boolean not null,
        result_published boolean default false not null,
        score_obtained numeric(5,2),
        created_at timestamp(6) not null,
        result_published_at timestamp(6),
        submitted_at timestamp(6),
        updated_at timestamp(6),
        assessment_id uuid not null,
        id uuid not null,
        result_published_by uuid,
        student_id uuid not null,
        status varchar(20) not null,
        paper_upload_url varchar(500),
        feedback TEXT,
        primary key (id)
    );

    create table teachers (
        id uuid not null,
        user_id uuid unique,
        bio TEXT,
        name varchar(255) not null,
        qualification varchar(255),
        subjects TEXT,
        title varchar(255),
        primary key (id)
    );

    create table users (
        is_deleted boolean not null,
        created_at timestamp(6) not null,
        updated_at timestamp(6),
        id uuid not null,
        phone_number varchar(20),
        status varchar(20),
        user_type varchar(30) not null,
        email varchar(150) not null unique,
        password_hash varchar(255) not null,
        primary key (id)
    );

    create index idx_batches_teacher_id 
       on batches (teacher_id);

    create index idx_batches_subject_id 
       on batches (subject_id);

    create index idx_batches_active_deleted 
       on batches (is_active, is_deleted);

    create index idx_schedule_batch_day 
       on class_schedules (batch_id, day_of_week);

    create index idx_schedule_day_time 
       on class_schedules (day_of_week, start_time);

    create index idx_enrollment_req_student 
       on enrollment_requests (student_id);

    create index idx_enrollment_req_batch 
       on enrollment_requests (batch_id);

    create index idx_enrollment_req_status 
       on enrollment_requests (status);

    create index idx_enrollments_batch_status 
       on enrollments (batch_id, status);

    create index idx_enrollments_student_status 
       on enrollments (student_id, status);

    create index idx_invoices_student_id 
       on invoices (student_id);

    create index idx_invoices_invoice_number 
       on invoices (invoice_number);

    create index idx_materials_batch_id 
       on learning_materials (batch_id);

    create index idx_notifications_recipient_created 
       on notifications (recipient_id, created_at, id);

    create index idx_notifications_recipient_read 
       on notifications (recipient_id, read_at);

    alter table if exists question_options 
       add constraint FKsb9v00wdrgc9qojtjkv7e1gkp 
       foreign key (question_id) 
       references questions;

    alter table if exists questions 
       add constraint FK2g7a2cfymnunug0860elnr447 
       foreign key (assessment_id) 
       references assessments;

    alter table if exists submission_answers 
       add constraint FKfonvi80ii6j4mnvfs1g7m2elb 
       foreign key (submission_id) 
       references submissions;
