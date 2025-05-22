CREATE TABLE Cuenta (
    ID_CUENTA VARCHAR(50) PRIMARY KEY,
    ID_CLIENTE VARCHAR(50) REFERENCES Cliente(ID_CLIENTE),
    SALDO DECIMAL(12, 2),
    TIPO_CUENTA VARCHAR(50)
);

INSERT INTO Cuenta (ID_CUENTA, ID_CLIENTE, SALDO, TIPO_CUENTA) VALUES
('CU001', 'C001', 1200.00, 'debito'),
('CU002', 'C001', 5000.00, 'credito'),
('CU003', 'C002', 300.00, 'debito'),
('CU004', 'C002', 2000.00, 'credito'),
('CU005', 'C003', 1500.00, 'debito'),
('CU006', 'C003', 4500.00, 'credito'),
('CU007', 'C004', 800.00, 'debito'),
('CU008', 'C004', 2500.00, 'credito'),
('CU009', 'C005', 600.00, 'debito'),
('CU010', 'C005', 3000.00, 'credito'),
('CU011', 'C006', 1000.00, 'debito'),
('CU012', 'C006', 4000.00, 'credito'),
('CU013', 'C007', 750.00, 'debito'),
('CU014', 'C007', 2200.00, 'credito'),
('CU015', 'C008', 950.00, 'debito'),
('CU016', 'C008', 4100.00, 'credito'),
('CU017', 'C009', 500.00, 'debito'),
('CU018', 'C009', 3100.00, 'credito'),
('CU019', 'C010', 1300.00, 'debito'),
('CU020', 'C010', 6000.00, 'credito');
