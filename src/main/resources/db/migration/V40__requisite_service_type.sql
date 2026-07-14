-- Part A: add service_type to creator_requisite

ALTER TABLE creator_requisite ADD COLUMN service_type VARCHAR(16);

UPDATE creator_requisite SET service_type = 'VIDEO';

ALTER TABLE creator_requisite ALTER COLUMN service_type SET NOT NULL;

-- Replace per-creator name uniqueness (was only enforced at service layer) with
-- per-(creator, service_type, name) DB-level unique index (case-insensitive on name).
CREATE UNIQUE INDEX uq_creator_requisite_creator_type_name
    ON creator_requisite (creator_user_id, service_type, lower(name));
