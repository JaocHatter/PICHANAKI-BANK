package com.parcial.central.handlers;

import com.parcial.central.services.WorkerNodeRegistry;
import com.parcial.central.services.WorkerNodeClient;
import com.parcial.central.services.WorkerNodeInfo;

import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Manejador HTTP para la operación de "Arqueo Total".
 * Espera una solicitud GET a /api/arqueo.
 * Contacta a todos los Nodos Trabajadores (o a los que tienen particiones de cuentas)
 * para sumar los saldos de todas las cuentas.
 * Esto puede ser una operación costosa y requiere una estrategia de agregación.
 */
public class ArqueoHandler extends BaseHttpHandler {
    private static final Logger LOGGER = Logger.getLogger(ArqueoHandler.class.getName());
    private final ExecutorService arqueoExecutor = Executors.newCachedThreadPool(); // Para consultas paralelas a workers
    private final WorkerNodeRegistry workerNodeRegistry;
    private final WorkerNodeClient workerNodeClient;

    public ArqueoHandler(WorkerNodeRegistry workerNodeRegistry, WorkerNodeClient workerNodeClient) {
        this.workerNodeRegistry = workerNodeRegistry;
        this.workerNodeClient = workerNodeClient;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendErrorResponse(exchange, 405, "Método no permitido. Usar GET.");
            return;
        }

        LOGGER.info("Solicitud de arqueo total recibida.");

        List<WorkerNodeInfo> allWorkers = workerNodeRegistry.getAllWorkerNodes();
        if (allWorkers.isEmpty()) {
            sendErrorResponse(exchange, 503, "No hay nodos trabajadores registrados para realizar el arqueo.");
            return;
        }

        // Realizar el arqueo consultando a cada nodo trabajador por su subtotal
        // o por las particiones que maneja. Aquí, por simplicidad, cada worker
        // podría tener un endpoint /api/worker/arqueoParcial.
        
        List<CompletableFuture<Double>> futures = allWorkers.stream()
            .map(worker -> CompletableFuture.supplyAsync(() -> {
                try {
                    // El path y los parámetros exactos dependerán de la API del Nodo Trabajador
                    String workerPath = "/api/worker/arqueoParcial"; // Ejemplo
                    CompletableFuture<String> responseFuture = workerNodeClient.sendGetRequestAsync(worker.getAddress(), workerPath, Map.of());
                    String responseStr = responseFuture.join();
                    return Double.parseDouble(responseStr);
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Fallo al obtener arqueo parcial del nodo " + worker.getId(), e);
                    return 0.0; // Considerar 0 si un nodo falla, o manejar el error de forma más estricta
                }
            }, arqueoExecutor))
            .collect(Collectors.toList());

        // Esperar a que todas las consultas a los workers terminen
        CompletableFuture<Void> allDoneFuture = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));

        try {
            allDoneFuture.get(); // Esperar a que todas las tareas se completen (o fallen)

            double totalGeneral = futures.stream()
                .mapToDouble(future -> {
                    try {
                        return future.getNow(0.0); // Obtener el resultado, 0.0 si no está completo (ya debería estarlo)
                    } catch (Exception e) {
                        LOGGER.log(Level.WARNING, "Error al obtener resultado de futuro de arqueo", e);
                        return 0.0;
                    }
                })
                .sum();
            
            LOGGER.log(Level.INFO, "Arqueo total calculado: {0}", totalGeneral);
            sendResponse(exchange, 200, "Arqueo Total del Sistema: " + String.format("%.2f", totalGeneral));

        } catch (Exception e) { // InterruptedException or ExecutionException
            LOGGER.log(Level.SEVERE, "Error durante la ejecución del arqueo total.", e);
            sendErrorResponse(exchange, 500, "Error interno del servidor durante el arqueo.");
        }
    }
}