-- Copyright (c) 2026 AMSIDEL Technologies. All rights reserved.
-- Website enquiries in the portal (Administration > Website enquiries): the sales team's status and notes on each one.

alter table sales_enquiries
    add column status varchar(20) not null default 'NEW'
        check ((status in ('NEW','CONTACTED','DEMO_SCHEDULED','WON','CLOSED'))),
    add column notes varchar(1000),
    add column updated_by_name varchar(120),
    add column updated_at timestamp(6) with time zone;

create index ix_sales_enquiry_status on sales_enquiries (status);
