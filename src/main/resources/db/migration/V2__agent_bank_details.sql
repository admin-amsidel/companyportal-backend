-- Copyright (c) 2026 IANEL Technologies. All rights reserved.
-- Agents: the bank account and IFSC their commission can be paid to (optional; UPI is the other way).

alter table agents add column bank_account_number varchar(30);
alter table agents add column ifsc_code varchar(11);
