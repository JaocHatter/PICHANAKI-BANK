package com.parcial.worker.persistence.dao;

import com.parcial.worker.persistence.DatabaseManager;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * DAO para operaciones relacionadas con la tabla Transacciones.
 */
public class TransaccionDAO {
    private static final Logger LOGGER = Logger.getLogger(TransaccionDAO.class.getName());
    private final DatabaseManager dbManager;
    private final String workerId;

    public TransaccionDAO(DatabaseManager dbManager, String workerId) {
        this.dbManager = dbManager; // Puede no ser necesario si solo se usan métodos con conexión explícita
        this.workerId = workerId;
    }

    /**
     * Registra una nueva transacción en la base de datos DENTRO DE UNA TRANSACCIÓN EXISTENTE.
     * @param conn La conexión de base de datos activa (manejada por el servicio).
     * @param idTransaccion ID único de la transacción.
     * @param idOrigen ID de la cuenta origen.
     * @param idDestino ID de la cuenta destino.
     * @param monto Monto de la transacción.
     * @param fechaHora Fecha y hora de la transacción.
     * @param estado Estado de la transacción (ej. "CONFIRMADA", "ERROR").
     * @return true si el registro fue exitoso, false en caso contrario.
     * @throws SQLException Si ocurre un error SQL.
     */
    public boolean registrarEnTransaccion(Connection conn, String idTransaccion, String idOrigen, 
                                        String idDestino, double monto, Timestamp fechaHora, String estado) throws SQLException {
        String sql = "INSERT INTO Transacciones (ID_TRANSACCION, ID_ORIGEN, ID_DESTINO, MONTO, FECHA_HORA, ESTADO) VALUES (?, ?, ?, ?, ?, ?)";
        LOGGER.log(Level.INFO, "Worker [{0}] DAO: Registrando transacción {1} (hilo: {2})", 
            new Object[]{workerId, idTransaccion, Thread.currentThread().getName()});

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, idTransaccion);
            pstmt.setString(2, idOrigen);
            pstmt.setString(3, idDestino);
            pstmt.setDouble(4, monto);
            pstmt.setTimestamp(5, fechaHora);
            pstmt.setString(6, estado);
            int affectedRows = pstmt.executeUpdate();
            return affectedRows > 0;
        }
        // La conexión no se cierra aquí, es manejada por el llamador (el handler de transferencia)
    }
}