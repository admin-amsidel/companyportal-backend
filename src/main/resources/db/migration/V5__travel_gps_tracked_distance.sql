-- Copyright (c) 2026 IANEL Technologies. All rights reserved.
-- GPS travel claims keep the shortest and longest route as tracked when the trip was completed. shortest_km and
-- longest_km are worked out again when a stop is moved; these two stay as tracked, next to the distance the person claimed
-- (distance_km). Trips completed before this: their current route is the best figure there is.

alter table travel_claims add column gps_shortest_km numeric(10,2);
alter table travel_claims add column gps_longest_km numeric(10,2);
update travel_claims set gps_shortest_km = shortest_km, gps_longest_km = longest_km
    where method = 'GPS' and shortest_km is not null;
