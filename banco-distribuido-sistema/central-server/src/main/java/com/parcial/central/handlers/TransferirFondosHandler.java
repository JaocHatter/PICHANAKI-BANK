package com.parcial.central.handlers;

import com.sun.net.httpserver.HttpExchange;
import com.parcial.central.services.WorkerNodeClient;
import com.parcial.central.services.WorkerNodeInfo;
import com.parcial.central.services.WorkerNodeRegistry;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class TransferirFondosHandler extends BaseHttpHandler {
    private static final Logger LOGGER = Logger.getLogger(TransferirFondosHandler.class.getName());
    private final WorkerNodeRegistry workerNodeRegistry;
    private final WorkerNodeClient workerNodeClient;

    public TransferirFondosHandler(WorkerNodeRegistry workerNodeRegistry, WorkerNodeClient workerNodeClient) {
        this.workerNodeRegistry = workerNodeRegistry;
        this.workerNodeClient = workerNodeClient;
    }
    
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendErrorResponse(exchange, 405, "Método no permitido. Usar POST.");
            return;
        }

        Map<String, String> formData = parseFormData(exchange);
        String cuentaOrigen = formData.get("cuentaOrigen");
        String cuentaDestino = formData.get("cuentaDestino");
        String montoStr = formData.get("monto");

        if (cuentaOrigen == null || cuentaDestino == null || montoStr == null) {
            sendErrorResponse(exchange, 400, "Parámetros 'cuentaOrigen', 'cuentaDestino', 'monto' son requeridos.");
            return;
        }
        // Validar monto, etc.
        LOGGER.log(Level.INFO, "Central Handler: Solicitud TRANSFERENCIA: {0} -> {1}, Monto: {2} (hilo: {3})",
                new Object[]{cuentaOrigen, cuentaDestino, montoStr, Thread.currentThread().getName()});

        // Lógica de enviar a las 3 réplicas de la partición de la cuenta origen.
        // Una transacción distribuida real es más compleja (2PC).
        // Aquí, asumimos que el worker puede manejar la transferencia si ambas cuentas están en su partición,
        // o al menos su parte. El Central asegura que la orden llegue a las réplicas.

        String partitionKeyOrigen = workerNodeRegistry.getPartitionKeyForAccount(cuentaOrigen);
        List<WorkerNodeInfo> replicasOrigen = workerNodeRegistry.getNodesForPartition(partitionKeyOrigen);

        if (replicasOrigen == null || replicasOrigen.size() < 3) { // Idealmente, verificar el factor de replicación
            LOGGER.warning("Central Handler: No hay suficientes réplicas para la partición de la cuenta origen: " + cuentaOrigen);
            sendErrorResponse(exchange, 503, "Servicio no disponible para procesar la transferencia (replicación insuficiente).");
            return;
        }

        Map<String, String> workerParams = Map.of(
                "cuentaOrigen", cuentaOrigen,
                "cuentaDestino", cuentaDestino,
                "monto", montoStr
        );

        List<CompletableFuture<String>> futures = new ArrayList<>();
        for (WorkerNodeInfo node : replicasOrigen) {
            futures.add(
                workerNodeClient.sendPostRequestAsync(
                    node.getAddress(),
                    "/api/worker/transferir",
                    workerParams
                ).exceptionally(ex -> {
                     LOGGER.log(Level.WARNING, "Central Handler: Fallo al enviar transferencia a nodo " + node.getId(), ex);
                     return "ERROR_NODO:" + node.getId() + ":" + ex.getMessage(); // Retornar un error identificable
                })
            );
        }

        // Esperar a que todas las réplicas respondan (o fallen)
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).whenComplete((voidResult, exAll) -> {
            try {
                if (exAll != null) {
                    LOGGER.log(Level.SEVERE, "Central Handler: Error en la ejecución de futuros de transferencia", exAll);
                    sendErrorResponse(exchange, 500, "Error interno masivo al procesar la transferencia.");
                    return;
                }

                long successCount = futures.stream()
                                           .map(CompletableFuture::join) // join es seguro aquí porque allOf ya completó
                                           .filter(response -> response != null && response.startsWith("CONFIRMACIÓN"))
                                           .count();

                // Lógica de quórum: ej. al menos 2 de 3 deben confirmar.
                // El PDF dice "el servidor central recepciona la respuesta del nodo trabajador y a su vez se envía el resultado al cliente"
                // Para simplicidad, si al menos uno confirma, lo tomamos (esto no es robusto para consistencia real).
                // En un sistema real, se requeriría una mayoría o todas las confirmaciones para la consistencia fuerte.
                
                String finalResponseToClient;
                int statusCodeToClient;

                if (successCount > 0) { // Simplificado: al menos una confirmación
                    finalResponseToClient = futures.stream()
                                                .map(CompletableFuture::join)
                                                .filter(r -> r != null && r.startsWith("CONFIRMACIÓN"))
                                                .findFirst()
                                                .orElse("CONFIRMACIÓN_PARCIAL: Verifique estado.");
                    statusCodeToClient = 200;
                    LOGGER.info("Central Handler: Transferencia procesada con " + successCount + " confirmaciones de workers.");

                } else {
                    String errorDetails = futures.stream()
                                                .map(CompletableFuture::join)
                                                .collect(Collectors.joining("; "));
                    finalResponseToClient = "ERROR: No se pudo confirmar la transferencia con los nodos. Detalles: " + errorDetails;
                    statusCodeToClient = 500; // Error del servidor o del worker
                    LOGGER.severe("Central Handler: Transferencia falló. Respuestas: " + errorDetails);
                }
                sendResponse(exchange, statusCodeToClient, finalResponseToClient);

            } catch (IOException ioe) {
                LOGGER.log(Level.SEVERE, "Central Handler: Error al enviar respuesta final de transferencia", ioe);
            }
        });
    }
}