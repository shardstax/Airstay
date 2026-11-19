UPDATE hotel
SET photos = ARRAY[
  CASE city
    WHEN 'Pune, Maharashtra, India' THEN '/api/v1/photos/b23ab7ce-0246-4f3f-92c1-49ea58bb2101'
    WHEN 'Hyderabad, Telangana, India' THEN '/api/v1/photos/b23ab7ce-0246-4f3f-92c1-49ea58bb2102'
    WHEN 'Bengaluru, Karnataka, India' THEN '/api/v1/photos/b23ab7ce-0246-4f3f-92c1-49ea58bb2103'
    WHEN 'Delhi, National Capital Territory of Delhi, India' THEN '/api/v1/photos/b23ab7ce-0246-4f3f-92c1-49ea58bb2104'
    WHEN 'Mumbai, Maharashtra, India' THEN '/api/v1/photos/b23ab7ce-0246-4f3f-92c1-49ea58bb2105'
    WHEN 'Nagpur, Maharashtra, India' THEN '/api/v1/photos/b23ab7ce-0246-4f3f-92c1-49ea58bb2106'
    WHEN 'Jaipur, Rajasthan, India' THEN '/api/v1/photos/b23ab7ce-0246-4f3f-92c1-49ea58bb2107'
  END,
  '/api/v1/photos/b23ab7ce-0246-4f3f-92c1-49ea58bb2001',
  '/api/v1/photos/b23ab7ce-0246-4f3f-92c1-49ea58bb2003'
]
WHERE owner_id = :demo_owner_id;

UPDATE room r
SET photos = ARRAY[
  CASE
    WHEN r.capacity >= 4 THEN '/api/v1/photos/b23ab7ce-0246-4f3f-92c1-49ea58bb2203'
    WHEN r.type ILIKE '%Twin%' THEN '/api/v1/photos/b23ab7ce-0246-4f3f-92c1-49ea58bb2202'
    ELSE '/api/v1/photos/b23ab7ce-0246-4f3f-92c1-49ea58bb2201'
  END,
  '/api/v1/photos/b23ab7ce-0246-4f3f-92c1-49ea58bb2002'
]
FROM hotel h
WHERE h.id = r.hotel_id
  AND h.owner_id = :demo_owner_id;
