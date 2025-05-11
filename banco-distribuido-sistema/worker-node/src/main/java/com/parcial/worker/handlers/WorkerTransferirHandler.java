package com.parcial.worker.handlers;

import com.sun.net.httpserver.HttpExchange;
import com.parcial.worker.persistence.DatabaseManager;
import com.parcial.worker.persistence.dao.CuentaDAO;
import com.parcial.worker.persistence.dao.TransaccionDAO; // Asumiento que existe
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Manejador para la operación de transferencia de fondos en un Nodo Trabajador.
 * Realiza la operación dentro de una transacción de base de datos.
 */
public class WorkerTransferirHandler extends BaseWorkerHandler { // Asumiendo que tienes una clase base
    private static final Logger LOGGER = Logger.getLogger(WorkerTransferirHandler.class.getName());
    private final CuentaDAO cuentaDAO;
    private final TransaccionDAO transaccionDAO; // Necesitas crear esta clase DAO

    public WorkerTransferirHandler(DatabaseManager dbManager, String workerId) {
        super(dbManager, workerId); // Llama al constructor de BaseWorkerHandler
        this.cuentaDAO = new CuentaDAO(dbManager, workerId);
        this.transaccionDAO = new TransaccionDAO(dbManager, workerId); // Inicializar
    }

    @Override
    protected void handleRequest(HttpExchange exchange, Map<String, String> params) throws IOException {
        // Los parámetros vendrán del cuerpo POST (form-urlencoded)
        String cuentaOrigen = params.get("cuentaOrigen");
        String cuentaDestino = params.get("cuentaDestino");
        String montoStr = params.get("monto");

        // ... (validación de parámetros como en el Central Server) ...
        if (cuentaOrigen == null || cuentaDestino == null || montoStr == null) {
            sendResponse(exchange, 400, "Error: Faltan parámetros para la transferencia.");
            return;
        }
        double monto;
        try {
            monto = Double.parseDouble(montoStr);
        } catch (NumberFormatException e) {
            sendResponse(exchange, 400, "Error: Monto inválido.");
            return;
        }

        LOGGER.log(Level.INFO, "Worker [{0}] API: Solicitud TRANSFERIR_FONDOS: {1} -> {2}, Monto: {3} (hilo: {4})", 
            new Object[]{workerId, cuentaOrigen, cuentaDestino, monto, Thread.currentThread().getName()});

        // Lógica de transacción JDBC
        Connection conn = null;
        String transaccionId = "TXN-" + workerId + "-" + System.currentTimeMillis(); // ID de transacción simple
        String estadoTransaccion = "ERROR";

        try {
            conn = dbManager.getConnection();
            conn.setAutoCommit(false); // Iniciar transacción

            // 1. Verificar saldo de la cuenta origen
            Optional<Double> saldoOrigenOpt = cuentaDAO.getSaldoEnTransaccion(conn, cuentaOrigen); // Necesitas un método que use la conn existente
            if (saldoOrigenOpt.isEmpty()) {
                throw new SQLException("Cuenta origen " + cuentaOrigen + " no encontrada.");
            }
            double saldoOrigen = saldoOrigenOpt.get();
            if (saldoOrigen < monto) {
                estadoTransaccion = "RECHAZADA_SALDO_INSUFICIENTE";
                throw new SQLException("Saldo insuficiente en cuenta " + cuentaOrigen);
            }

            // 2. Debitar de cuenta origen
            boolean debitado = cuentaDAO.updateSaldoEnTransaccion(conn, cuentaOrigen, saldoOrigen - monto);
            if (!debitado) {
                throw new SQLException("No se pudo debitar de la cuenta origen " + cuentaOrigen);
            }
            LOGGER.info("Worker ["+workerId+"] Débito realizado en " + cuentaOrigen + " por " + monto);
            
            // 3. Acreditar a cuenta destino (SI ESTE WORKER MANEJA LA CUENTA DESTINO)
            // En un sistema real, si cuentaDestino está en otra partición/worker,
            // el Servidor Central coordinaría esto. Aquí asumimos que puede ser local.
            // Para simplificar, si la cuenta destino NO es manejada por este worker,
            // esta parte se omitiría aquí y el Central lo manejaría con otro worker.
            // Vamos a simular que intentamos acreditar. Si no existe localmente, podría fallar o el DAO manejarlo.
            
            Optional<Double> saldoDestinoOpt = cuentaDAO.getSaldoEnTransaccion(conn, cuentaDestino);
            if (saldoDestinoOpt.isEmpty()) {
                 // Podría ser que la cuenta destino esté en otro worker.
                 // Por ahora, si no está local, la transacción global fallaría o requeriría 2PC.
                 // Aquí asumiremos que si no está, es un error para esta lógica simple.
                 throw new SQLException("Cuenta destino " + cuentaDestino + " no encontrada en este nodo para acreditación.");
            }
            double saldoDestino = saldoDestinoOpt.get();
            boolean acreditado = cuentaDAO.updateSaldoEnTransaccion(conn, cuentaDestino, saldoDestino + monto);
            if (!acreditado) {
                throw new SQLException("No se pudo acreditar a la cuenta destino " + cuentaDestino);
            }
            LOGGER.info("Worker ["+workerId+"] Crédito realizado en " + cuentaDestino + " por " + monto);

            // 4. Registrar la transacción
            estadoTransaccion = "CONFIRMADA";
            // Necesitas un método en TransaccionDAO: registrarEnTransaccion(conn, id, origen, destino, monto, estado)
            transaccionDAO.registrarEnTransaccion(conn, transaccionId, cuentaOrigen, cuentaDestino, monto, Timestamp.valueOf(LocalDateTime.now()), estadoTransaccion);
            
            conn.commit(); // Confirmar transacción
            LOGGER.info("Worker ["+workerId+"] Transacción " + transaccionId + " COMPLETADA y commit realizado.");
            sendResponse(exchange, 200, "CONFIRMACIÓN: Transferencia " + transaccionId + " realizada.");

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Worker ["+workerId+"] Error en transacción de transferencia " + transaccionId + ": " + e.getMessage(), e);
            if (conn != null) {
                try {
                    LOGGER.warning("Worker ["+workerId+"] Intentando rollback para transacción " + transaccionId);
                    conn.rollback(); // Revertir transacción en caso de error
                } catch (SQLException ex) {
                    LOGGER.log(Level.SEVERE, "Worker ["+workerId+"] Error durante el rollback para transacción " + transaccionId, ex);
                }
            }
            sendResponse(exchange, 500, "ERROR: " + estadoTransaccion + " - " + e.getMessage());
        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(true); // Restaurar auto-commit
                    conn.close(); // Cerrar conexión
                } catch (SQLException e) {
                    LOGGER.log(Level.SEVERE, "Worker ["+workerId+"] Error al cerrar conexión después de transferencia " + transaccionId, e);
                }
            }
        }
    }
     // Asumimos que tienes una clase BaseWorkerHandler como esta:
    // import com.sun.net.httpserver.HttpExchange;
    // import com.sun.net.httpserver.HttpHandler;
    // import java.io.IOException;
    // import java.io.OutputStream;
    // import java.io.UnsupportedEncodingException;
    // import java.net.URLDecoder;
    // import java.nio.charset.StandardCharsets;
    // import java.util.Arrays;
    // import java.util.Collections;
    // import java.util.Map;
    // import java.util.logging.Level;
    // import java.util.logging.Logger;
    // import java.util.stream.Collectors;
    // public abstract class BaseWorkerHandler implements HttpHandler {
    //     // ... (código de parseQueryParams, parseFormData, sendResponse que te di antes) ...
    //     protected final DatabaseManager dbManager; // Añadir esto
    //     protected final String workerId;      // Añadir esto

    //     public BaseWorkerHandler(DatabaseManager dbManager, String workerId) { // Modificar constructor
    //         this.dbManager = dbManager;
    //         this.workerId = workerId;
    //     }
        
    //     @Override
    //     public void handle(HttpExchange exchange) throws IOException {
    //         Map<String, String> params;
    //         if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
    //             params = parseFormData(exchange);
    //         } else { // Asumir GET
    //             params = parseQueryParams(exchange.getRequestURI().getQuery());
    //         }
    //         handleRequest(exchange, params);
    //     }

    //     protected abstract void handleRequest(HttpExchange exchange, Map<String, String> params) throws IOException;
        
    //     // ... (métodos sendResponse, parseQueryParams, parseFormData)
    //     protected Map<String, String> parseQueryParams(String query) { /* ... */ }
    //     protected Map<String, String> parseFormData(HttpExchange exchange) throws IOException { /* ... */ }
    //     protected void sendResponse(HttpExchange exchange, int statusCode, String responseBody) throws IOException { /* ... */ }
    // }
}