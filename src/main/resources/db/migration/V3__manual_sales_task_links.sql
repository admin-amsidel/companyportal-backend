-- Copyright (c) 2026 IANEL Technologies. All rights reserved.
-- Manual Sales tasks: the project they are for (customer, project and agent details come from it) and the Team
-- performance goal they were made for (its person is the task's one assignee). Both optional; a deleted project or goal
-- only unlinks the task, which keeps the names it showed.

alter table manual_sales_tasks add column project_id bigint;
alter table manual_sales_tasks add column goal_id bigint;
alter table manual_sales_tasks add constraint fk_manual_sales_task_project foreign key (project_id) references projects on delete set null;
alter table manual_sales_tasks add constraint fk_manual_sales_task_goal foreign key (goal_id) references goals on delete set null;
