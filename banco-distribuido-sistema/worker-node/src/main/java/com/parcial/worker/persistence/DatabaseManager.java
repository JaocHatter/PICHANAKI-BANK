package com.parcial.worker.persistence;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Gestiona las conexiones a la base de datos PostgreSQL local del Nodo Trabajador.
 * IMPORTANTE: Esta implementación gestiona conexiones directamente sin un pool de conexiones
 * de terceros para cumplir con restricciones estrictas. NO ES RECOMENDABLE PARA PRODUCCIÓN.
 */
public class DatabaseManager {
    private static final Logger LOGGER = Logger.getLogger(DatabaseManager.class.getName());
    private final String dbUrl;
    private final String dbUser;
    private final String dbPassword;

    public DatabaseManager(String dbUrl, String dbUser, String dbPassword) {
        this.dbUrl = dbUrl;
        this.dbUser = dbUser;
        this.dbPassword = dbPassword;
        try {
            // Cargar el driver de PostgreSQL
            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException e) {
            LOGGER.log(Level.SEVERE, "Error al cargar el driver de PostgreSQL JDBC", e);
            throw new RuntimeException("Driver de PostgreSQL no encontrado", e);
        }
    }

    /**
     * Obtiene una nueva conexión a la base de datos.
     * El llamador es responsable de cerrar esta conexión.
     * @return Una conexión JDBC.
     * @throws SQLException Si ocurre un error al conectar.
     */
    public Connection getConnection() throws SQLException {
        // Cada llamada crea una nueva conexión. Ineficiente pero cumple la restricción.
        long startTime = System.nanoTime();
        Connection connection = DriverManager.getConnection(this.dbUrl, this.dbUser, this.dbPassword);
        long endTime = System.nanoTime();
        LOGGER.log(Level.FINE, "Conexión a DB obtenida en {0} ms (hilo: {1})", new Object[]{ (endTime - startTime) / 1_000_000, Thread.currentThread().getName()});
        return connection;
    }
}