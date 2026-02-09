-- Create db-scheduler table for distributed task coordination
CREATE TABLE scheduled_tasks (
    task_name VARCHAR(255) NOT NULL,
    task_instance VARCHAR(255) NOT NULL,
    task_data BYTEA,
    execution_time TIMESTAMP NOT NULL,
    picked BOOLEAN NOT NULL,
    picked_by VARCHAR(255),
    last_success TIMESTAMP,
    last_failure TIMESTAMP,
    consecutive_failures INT,
    last_heartbeat TIMESTAMP,
    version BIGINT NOT NULL,
    PRIMARY KEY (task_name, task_instance)
);

CREATE INDEX idx_scheduled_tasks_execution_time ON scheduled_tasks(execution_time);
CREATE INDEX idx_scheduled_tasks_picked ON scheduled_tasks(picked, execution_time);
