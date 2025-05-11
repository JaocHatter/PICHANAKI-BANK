package com.parcial.worker.handlers;

import com.sun.net.httpserver.HttpExchange;
import com.parcial.worker.persistence.DatabaseManager;
import com.parcial.worker.persistence.dao.CuentaDAO;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

public class WorkerSaldoHandler extends BaseWorkerHandler {
    private static final Logger LOGGER = Logger.getLogger(WorkerSaldoHandler.class.getName());
    private final CuentaDAO cuentaDAO;

    public WorkerSaldoHandler(DatabaseManager dbManager, String workerId) {
        super(dbManager, workerId); // Si BaseWorkerHandler toma estos params
        this.cuentaDAO = new CuentaDAO(dbManager, workerId);
    }

    @Override
    protected void handleRequest(HttpExchange exchange, Map<String, String> params) throws IOException {
        String cuentaId = params.get("cuentaId");
        if (cuentaId == null || cuentaId.trim().isEmpty()) {
            sendResponse(exchange, 400, "Error: Parámetro 'cuentaId' es requerido.");
            return;
        }
        // El hilo actual es del ThreadPool del HttpServer del Worker
        LOGGER.log(Level.INFO, "Worker [{0}] API: Solicitud SALDO para cuenta: {1} (hilo: {2})", 
            new Object[]{workerId, cuentaId, Thread.currentThread().getName()});

        Optional<Double> saldoOpt = cuentaDAO.getSaldo(cuentaId);

        if (saldoOpt.isPresent()) {
            sendResponse(exchange, 200, String.valueOf(saldoOpt.get()));
        } else {
            sendResponse(exchange, 404, "Error: Cuenta " + cuentaId + " no encontrada en este nodo.");
        }
    }
}