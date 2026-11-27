-- Copyright (c) 2026 IANEL Technologies. All rights reserved.
-- Door & window quotes get the two printed formats (Table and Design layout), lines of any kind in order (designs,
-- items in sq ft, numbers, running feet or lump sum, and section headings), transport, a discount in % or rupees,
-- GST added or included, and the printed texts (terms, cover letter, bank details).

alter table window_quotes add column format varchar(6) not null default 'DESIGN' check ((format in ('TABLE','DESIGN')));
alter table window_quotes add column transport numeric(14,2) not null default 0;

-- the discount: a percentage or an amount (the old column only held a percentage up to 999.99)
alter table window_quotes add column discount_value numeric(14,2) not null default 0;
alter table window_quotes add column discount_type varchar(3) not null default 'PCT' check ((discount_type in ('PCT','AMT')));
update window_quotes set discount_value = discount_pct;
alter table window_quotes drop column discount_pct;

-- GST: added on top or included in the rates
alter table window_quotes add column gst_mode varchar(4) not null default 'ADD' check ((gst_mode in ('ADD','INCL')));
alter table window_quotes add column gst_pct numeric(5,2) not null default 0;
alter table window_quotes add column gst numeric(14,2) not null default 0;
update window_quotes set gst_pct = tax_pct, gst = tax;
alter table window_quotes drop column tax_pct;
alter table window_quotes drop column tax;

-- lines replace items; older quotations keep their items and are read as design lines
alter table window_quotes alter column items_json drop not null;
alter table window_quotes add column lines_json text;
alter table window_quotes add column options_json text;
