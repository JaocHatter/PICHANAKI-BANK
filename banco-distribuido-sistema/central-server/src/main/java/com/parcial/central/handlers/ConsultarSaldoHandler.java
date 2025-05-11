package com.parcial.central.handlers;

import com.sun.net.httpserver.HttpExchange;
import com.parcial.central.services.WorkerNodeClient;
import com.parcial.central.services.WorkerNodeInfo;
import com.parcial.central.services.WorkerNodeRegistry;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ConsultarSaldoHandler extends BaseHttpHandler {
    private static final Logger LOGGER = Logger.getLogger(ConsultarSaldoHandler.class.getName());
    private final WorkerNodeRegistry workerNodeRegistry;
    private final WorkerNodeClient workerNodeClient;

    public ConsultarSaldoHandler(WorkerNodeRegistry workerNodeRegistry, WorkerNodeClient workerNodeClient) {
        this.workerNodeRegistry = workerNodeRegistry;
        this.workerNodeClient = workerNodeClient;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendErrorResponse(exchange, 405, "Método no permitido. Usar GET.");
            return;
        }

        Map<String, String> queryParams = parseQueryParams(exchange.getRequestURI().getQuery());
        String cuentaId = queryParams.get("cuentaId");

        if (cuentaId == null || cuentaId.trim().isEmpty()) {
            sendErrorResponse(exchange, 400, "Parámetro 'cuentaId' es requerido.");
            return;
        }
        // El hilo actual es del ThreadPool del HttpServer del Central Server
        LOGGER.log(Level.INFO, "Central Handler: Solicitud SALDO para cuenta: {0} (hilo: {1})", 
            new Object[]{cuentaId, Thread.currentThread().getName()});

        String partitionKey = workerNodeRegistry.getPartitionKeyForAccount(cuentaId); // Lógica para determinar la partición
        List<WorkerNodeInfo> candidateNodes = workerNodeRegistry.getNodesForPartition(partitionKey);

        if (candidateNodes == null || candidateNodes.isEmpty()) {
            LOGGER.log(Level.WARNING, "No se encontraron nodos trabajadores para la partición de la cuenta: {0}", cuentaId);
            sendErrorResponse(exchange, 503, "Servicio no disponible para la cuenta " + cuentaId);
            return;
        }

        CompletableFuture<String> resultFuture = null;
        // Intentar con la primera réplica, luego las otras en caso de fallo (failover simple)
        // Una estrategia más avanzada usaría CompletableFuture.anyOf o un loop con reintentos
        for (WorkerNodeInfo node : candidateNodes) {
            CompletableFuture<String> future = workerNodeClient.sendGetRequestAsync(
                node.getAddress(), // ej. http://worker-db-0.worker-db-svc:8081
                "/api/worker/saldo",
                Map.of("cuentaId", cuentaId)
            ).handle((response, ex) -> { // El .handle se ejecuta en el pool del WorkerNodeClient o ForkJoinPool
                if (ex != null) {
                    LOGGER.log(Level.WARNING, "Fallo al contactar nodo " + node.getId() + " para saldo de cuenta " + cuentaId + " (hilo handle: " + Thread.currentThread().getName() + ")", ex);
                    return null; // Indica fallo para este nodo
                }
                return response;
            });
            if (resultFuture == null) {
                resultFuture = future;
            } else {
                // Si el intento anterior falló (resultFuture completó con null o excepción), intentar con el siguiente
                resultFuture = resultFuture.thenCompose(res -> {
                    if (res != null) return CompletableFuture.completedFuture(res); // Ya tenemos respuesta
                    return future; // Intentar con el siguiente futuro
                });
            }
        }
        
        if (resultFuture == null) { // No debería pasar si candidateNodes no está vacío
             sendErrorResponse(exchange, 500, "Error interno: no se pudo iniciar la consulta de saldo.");
             return;
        }

        resultFuture.whenComplete((saldoResponse, ex) -> { // Se ejecuta en el pool del WorkerNodeClient o FJP
            // ESTE BLOQUE SE EJECUTA EN UN HILO DEL COMPLETABLEFUTURE, NO EN EL HILO ORIGINAL DEL HANDLER HTTP.
            // La escritura de la respuesta HTTP DEBE volver al hilo del Exchange o ser manejada cuidadosamente.
            // Por simplicidad aquí, y dado que sendResponse es síncrono, esto podría funcionar,
            // pero en sistemas de alta carga, se debe tener cuidado con el contexto del hilo.
            try {
                if (ex != null || saldoResponse == null) {
                    LOGGER.log(Level.SEVERE, "Central Handler: Error final al obtener saldo para cuenta " + cuentaId + " (hilo: " + Thread.currentThread().getName() + ")", ex);
                    sendErrorResponse(exchange, 500, "Error al procesar consulta de saldo para cuenta " + cuentaId + ".");
                } else {
                    LOGGER.log(Level.INFO, "Central Handler: Saldo para cuenta {0}: {1} (hilo: {2})", new Object[]{cuentaId, saldoResponse, Thread.currentThread().getName()});
                    sendResponse(exchange, 200, "Saldo para cuenta " + cuentaId + ": " + saldoResponse);
                }
            } catch (IOException ioe) {
                LOGGER.log(Level.SEVERE, "Central Handler: Error al enviar respuesta para saldo de cuenta " + cuentaId, ioe);
            }
        });
    }
}