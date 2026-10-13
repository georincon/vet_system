INSERT INTO veterinario (usuario_id, nombres, apellidos, activo)
SELECT u.id, COALESCE(u.nombre, u.username), '', true
FROM usuario u
WHERE u.rol = 'VETERINARIO'
  AND NOT EXISTS (SELECT 1 FROM veterinario v WHERE v.usuario_id = u.id);
