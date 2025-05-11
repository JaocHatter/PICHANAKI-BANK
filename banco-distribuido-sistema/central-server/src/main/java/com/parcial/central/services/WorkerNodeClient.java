package com.parcial.central.services;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService; // Usar ExecutorService para llamadas asíncronas
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Cliente HTTP para que el Nodo Central se comunique con los Nodos Trabajadores.
 * Utiliza HttpClient de Java 11+ y un ExecutorService para gestionar las llamadas asíncronas.
 */
public class WorkerNodeClient {
    private static final Logger LOGGER = Logger.getLogger(WorkerNodeClient.class.getName());
    private final HttpClient httpClient;
    private final ExecutorService executorService; // Pool de hilos para las llamadas HTTP salientes

    public WorkerNodeClient(ExecutorService executorService) {
        this.executorService = executorService;
        this.httpClient = HttpClient.newBuilder()
                .executor(this.executorService) // HttpClient usará este pool para operaciones asíncronas
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        LOGGER.info("WorkerNodeClient inicializado con ExecutorService dedicado.");
    }

    /**
     * Envía una solicitud GET asíncrona a un Nodo Trabajador.
     * La operación se ejecuta en un hilo del ExecutorService configurado.
     * @param workerNodeAddress URL base del API del nodo trabajador.
     * @param path Path específico del endpoint.
     * @param params Mapa de parámetros para la query string.
     * @return CompletableFuture que contendrá la respuesta del trabajador como String.
     */
    public CompletableFuture<String> sendGetRequestAsync(String workerNodeAddress, String path, Map<String, String> params) {
        String queryString = params.entrySet().stream()
                .map(entry -> URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8) + "=" + URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));

        URI uri = URI.create(workerNodeAddress + path + (queryString.isEmpty() ? "" : "?" + queryString));
        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();

        LOGGER.log(Level.INFO, "Enviando GET asíncrono a {0} (hilo: {1})", new Object[]{uri, Thread.currentThread().getName()});

        // sendAsync usa el executor del HttpClient (que configuramos nosotros) o el default.
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    LOGGER.log(Level.INFO, "Respuesta de {0}: {1} (hilo: {2})", new Object[]{uri, response.statusCode(), Thread.currentThread().getName()});
                    if (response.statusCode() >= 200 && response.statusCode() < 300) {
                        return response.body();
                    } else {
                        // En un escenario real, lanzar una excepción más específica.
                        throw new RuntimeException("Solicitud GET fallida a " + uri + " con estado: " + response.statusCode() + " Body: " + response.body());
                    }
                });
    }

    /**
     * Envía una solicitud POST asíncrona a un Nodo Trabajador.
     * La operación se ejecuta en un hilo del ExecutorService configurado.
     * @param workerNodeAddress URL base del API del nodo trabajador.
     * @param path Path específico del endpoint.
     * @param formData Mapa de datos para enviar como 'application/x-www-form-urlencoded'.
     * @return CompletableFuture que contendrá la respuesta del trabajador como String.
     */
    public CompletableFuture<String> sendPostRequestAsync(String workerNodeAddress, String path, Map<String, String> formData) {
        String formBody = formData.entrySet().stream()
                .map(entry -> URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8) + "=" + URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));

        URI uri = URI.create(workerNodeAddress + path);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(formBody))
                .build();
        
        LOGGER.log(Level.INFO, "Enviando POST asíncrono a {0} con form data: {1} (hilo: {2})", new Object[]{uri, formBody, Thread.currentThread().getName()});

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    LOGGER.log(Level.INFO, "Respuesta de {0}: {1} (hilo: {2})", new Object[]{uri, response.statusCode(), Thread.currentThread().getName()});
                    if (response.statusCode() >= 200 && response.statusCode() < 300) {
                        return response.body();
                    } else {
                        throw new RuntimeException("Solicitud POST fallida a " + uri + " con estado: " + response.statusCode() + " Body: " + response.body());
                    }
                });
    }
    
    // Método para cerrar el ExecutorService cuando el servidor se detiene
    public void shutdown() {
        LOGGER.info("Cerrando ExecutorService de WorkerNodeClient...");
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
        LOGGER.info("ExecutorService de WorkerNodeClient cerrado.");
    }
}