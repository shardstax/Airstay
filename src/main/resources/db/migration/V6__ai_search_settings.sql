ALTER TABLE app_user_roles DROP CONSTRAINT app_user_roles_role_check;
ALTER TABLE app_user_roles ADD CONSTRAINT app_user_roles_role_check
    CHECK (role IN ('GUEST', 'HOTEL_MANAGER', 'ADMIN'));

CREATE TABLE ai_search_settings (
    id INTEGER PRIMARY KEY CHECK (id = 1),
    enabled BOOLEAN NOT NULL
);

INSERT INTO ai_search_settings (id, enabled) VALUES (1, TRUE);
