ALTER TABLE enrollment
    ADD COLUMN price DECIMAL(10, 2) NOT NULL DEFAULT 0 AFTER user_id;

UPDATE enrollment e
    JOIN course c ON e.course_id = c.id
    SET e.price = c.price;
