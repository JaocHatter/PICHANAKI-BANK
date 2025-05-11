package com.parcial.worker.handlers;

import com.sun.net.httpserver.HttpExchange;
import com.parcial.worker.persistence.DatabaseManager;
import com.parcial.worker.persistence.dao.CuentaDAO;

import java.io.IOException;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class WorkerArqueoParcialHandler extends BaseWorkerHandler {
    private static final Logger LOGGER = Logger.getLogger(WorkerArqueoParcialHandler.class.getName());
    private final CuentaDAO cuentaDAO;

    public WorkerArqueoParcialHandler(DatabaseManager dbManager, String workerId) {
        super(dbManager, workerId);
        this.cuentaDAO = new CuentaDAO(dbManager, workerId);
    }

    @Override
    protected void handleRequest(HttpExchange exchange, Map<String, String> params) throws IOException {
        LOGGER.log(Level.INFO, "Worker [{0}] API: Solicitud ARQUEO_PARCIAL (hilo: {1})", 
            new Object[]{workerId, Thread.currentThread().getName()});
        
        double arqueoParcial = cuentaDAO.getArqueoParcial();
        sendResponse(exchange, 200, String.valueOf(arqueoParcial));
    }
}