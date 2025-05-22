CREATE TABLE Cliente (
    ID_CLIENTE VARCHAR(50) PRIMARY KEY,
    NOMBRE VARCHAR(100),
    EMAIL VARCHAR(100),
    TELEFONO VARCHAR(20)
);

INSERT INTO Cliente (ID_CLIENTE, NOMBRE, EMAIL, TELEFONO) VALUES
('C001', 'Ana Torres', 'ana.torres@mail.com', '987654321'),
('C002', 'Luis Ramos', 'luis.ramos@mail.com', '986543210'),
('C003', 'Carmen Soto', 'carmen.soto@mail.com', '985432109'),
('C004', 'Jorge Mena', 'jorge.mena@mail.com', '984321098'),
('C005', 'Lucía Vidal', 'lucia.vidal@mail.com', '983210987'),
('C006', 'Pedro Quispe', 'pedro.quispe@mail.com', '982109876'),
('C007', 'Sofía Paredes', 'sofia.paredes@mail.com', '981098765'),
('C008', 'Carlos Rivas', 'carlos.rivas@mail.com', '980987654'),
('C009', 'Elena Castañeda', 'elena.castaneda@mail.com', '979876543'),
('C010', 'Marco Silva', 'marco.silva@mail.com', '978765432');
