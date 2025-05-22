CREATE TABLE Transacciones (
    ID_TRANSACCION VARCHAR(50) PRIMARY KEY,
    ID_ORIGEN VARCHAR(50) REFERENCES Cuenta(ID_CUENTA),
    ID_DESTINO VARCHAR(50) REFERENCES Cuenta(ID_CUENTA),
    MONTO DECIMAL(12, 2),
    FECHA_HORA TIMESTAMP,
    ESTADO VARCHAR(20)
);

INSERT INTO Transacciones (ID_TRANSACCION, ID_ORIGEN, ID_DESTINO, MONTO, FECHA_HORA, ESTADO) VALUES
('T001', 'CU001', 'CU003', 200.00, '2025-05-20 09:00:00', 'completado'),
('T002', 'CU005', 'CU007', 150.00, '2025-05-20 10:00:00', 'completado'),
('T003', 'CU009', 'CU011', 100.00, '2025-05-20 11:00:00', 'completado'),
('T004', 'CU013', 'CU015', 50.00, '2025-05-20 12:00:00', 'completado'),
('T005', 'CU017', 'CU019', 120.00, '2025-05-20 13:00:00', 'completado'),
('T006', 'CU003', 'CU002', 90.00, '2025-05-20 14:00:00', 'completado'),
('T007', 'CU004', 'CU006', 300.00, '2025-05-20 15:00:00', 'rechazado'),
('T008', 'CU008', 'CU010', 220.00, '2025-05-20 16:00:00', 'completado'),
('T009', 'CU012', 'CU014', 180.00, '2025-05-20 17:00:00', 'pendiente'),
('T010', 'CU016', 'CU018', 260.00, '2025-05-20 18:00:00', 'completado'),
('T011', 'CU020', 'CU001', 400.00, '2025-05-20 19:00:00', 'completado'),
('T012', 'CU002', 'CU004', 100.00, '2025-05-21 09:00:00', 'completado'),
('T013', 'CU006', 'CU008', 250.00, '2025-05-21 10:00:00', 'completado'),
('T014', 'CU010', 'CU012', 130.00, '2025-05-21 11:00:00', 'rechazado'),
('T015', 'CU014', 'CU016', 300.00, '2025-05-21 12:00:00', 'completado');
