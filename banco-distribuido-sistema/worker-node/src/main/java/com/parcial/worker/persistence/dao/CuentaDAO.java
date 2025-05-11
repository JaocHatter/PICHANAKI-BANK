package com.parcial.worker.persistence.dao;

import com.parcial.worker.persistence.DatabaseManager;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * DAO para operaciones relacionadas con la tabla Cuenta.
 */
public class CuentaDAO {
    private static final Logger LOGGER = Logger.getLogger(CuentaDAO.class.getName());
    private final DatabaseManager dbManager;
    private final String workerId;


    public CuentaDAO(DatabaseManager dbManager, String workerId) {
        this.dbManager = dbManager;
        this.workerId = workerId;
    }

    /**
     * Obtiene el saldo de una cuenta específica.
     * @param cuentaId El ID de la cuenta.
     * @return Optional con el saldo si la cuenta existe, sino Optional.empty().
     */
    public Optional<Double> getSaldo(String cuentaId) {
        String sql = "SELECT SALDO FROM Cuenta WHERE ID_CUENTA = ?";
        // El hilo que ejecuta este método es un hilo del ThreadPool del HttpServer del Worker.
        LOGGER.log(Level.INFO, "Worker [{0}] DAO: Consultando saldo para cuenta {1} (hilo: {2})", new Object[]{workerId, cuentaId, Thread.currentThread().getName()});

        try (Connection conn = dbManager.getConnection(); // Obtiene y cierra conexión
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, cuentaId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(rs.getDouble("SALDO"));
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Worker ["+workerId+"] Error al consultar saldo para cuenta " + cuentaId, e);
        }
        return Optional.empty();
    }

    /**
     * Obtiene el saldo de una cuenta específica USANDO UNA CONEXIÓN EXISTENTE (para transacciones).
     * @param conn La conexión de base de datos activa.
     * @param cuentaId El ID de la cuenta.
     * @return Optional con el saldo si la cuenta existe, sino Optional.empty().
     * @throws SQLException Si ocurre un error SQL.
     */
    public Optional<Double> getSaldoEnTransaccion(Connection conn, String cuentaId) throws SQLException {
        String sql = "SELECT SALDO FROM Cuenta WHERE ID_CUENTA = ?";
        LOGGER.log(Level.FINE, "Worker [{0}] DAO Tx: Consultando saldo para cuenta {1} (hilo: {2})", new Object[]{workerId, cuentaId, Thread.currentThread().getName()});
        
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, cuentaId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(rs.getDouble("SALDO"));
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Actualiza el saldo de una cuenta. Usado dentro de una transacción.
     * @param conn La conexión de base de datos (manejada por el servicio que orquesta la transacción).
     * @param cuentaId El ID de la cuenta.
     * @param nuevoSaldo El nuevo saldo.
     * @return true si la actualización fue exitosa, false en caso contrario.
     * @throws SQLException Si ocurre un error SQL.
     */
    public boolean updateSaldoEnTransaccion(Connection conn, String cuentaId, double nuevoSaldo) throws SQLException {
        String sql = "UPDATE Cuenta SET SALDO = ? WHERE ID_CUENTA = ?";
        LOGGER.log(Level.INFO, "Worker [{0}] DAO: Actualizando saldo para cuenta {1} a {2} (hilo: {3})", new Object[]{workerId, cuentaId, nuevoSaldo, Thread.currentThread().getName()});
        
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setDouble(1, nuevoSaldo);
            pstmt.setString(2, cuentaId);
            int affectedRows = pstmt.executeUpdate();
            return affectedRows > 0;
        }
        // La conexión no se cierra aquí, se maneja externamente por la transacción
    }

    /**
     * Suma los saldos de todas las cuentas gestionadas por este worker.
     * @return La suma total de los saldos.
     */
    public double getArqueoParcial() {
        String sql = "SELECT SUM(SALDO) AS TOTAL_SALDO FROM Cuenta";
        LOGGER.log(Level.INFO, "Worker [{0}] DAO: Calculando arqueo parcial (hilo: {1})", new Object[]{workerId, Thread.currentThread().getName()});
        double totalSaldo = 0.0;

        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            
            if (rs.next()) {
                totalSaldo = rs.getDouble("TOTAL_SALDO");
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Worker ["+workerId+"] Error al calcular arqueo parcial", e);
        }
        LOGGER.log(Level.INFO, "Worker [{0}] DAO: Arqueo parcial calculado: {1}", new Object[]{workerId, totalSaldo});
        return totalSaldo;
    }
}