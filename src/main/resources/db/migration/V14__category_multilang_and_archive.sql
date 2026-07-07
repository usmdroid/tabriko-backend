ALTER TABLE categories
    ADD COLUMN name_ru VARCHAR(100),
    ADD COLUMN name_en VARCHAR(100),
    ADD COLUMN archived BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE categories SET name_ru = name, name_en = name WHERE name_ru IS NULL OR name_en IS NULL;

ALTER TABLE categories ALTER COLUMN name_ru SET NOT NULL;
ALTER TABLE categories ALTER COLUMN name_en SET NOT NULL;
