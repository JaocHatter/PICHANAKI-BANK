package com.parcial.worker.handlers;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import com.parcial.worker.persistence.DatabaseManager;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Clase base abstracta para los manejadores HTTP del Nodo Trabajador.
 */
public abstract class BaseWorkerHandler implements HttpHandler {
    private static final Logger LOGGER_BASE_WORKER_HANDLER = Logger.getLogger(BaseWorkerHandler.class.getName());
    
    // Los DAOs o el DatabaseManager se pasarán a los constructores de los handlers específicos.
    // No es necesario tenerlos aquí si cada handler los recibe.
    protected final DatabaseManager dbManager;
    protected final String workerId;

    public BaseWorkerHandler(DatabaseManager dbManager, String workerId) {
         this.dbManager = dbManager;
         this.workerId = workerId;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        Map<String, String> params;
        String requestMethod = exchange.getRequestMethod();
        // El hilo actual es del ThreadPool del HttpServer del Worker
        LOGGER_BASE_WORKER_HANDLER.log(Level.FINE, "Worker Handler Base: Recibida solicitud {0} a {1} (hilo: {2})", 
            new Object[]{requestMethod, exchange.getRequestURI().getPath(), Thread.currentThread().getName()});

        if ("POST".equalsIgnoreCase(requestMethod)) {
            params = parseFormData(exchange);
        } else if ("GET".equalsIgnoreCase(requestMethod)) {
            params = parseQueryParams(exchange.getRequestURI().getQuery());
        } else {
            sendResponse(exchange, 405, "Método no Soportado");
            return;
        }
        handleRequest(exchange, params); // Delegar a la implementación específica
    }

    /**
     * Método abstracto para ser implementado por handlers específicos.
     */
    protected abstract void handleRequest(HttpExchange exchange, Map<String, String> params) throws IOException;


    protected Map<String, String> parseQueryParams(String query) {
        if (query == null || query.isEmpty()) {
            return Collections.emptyMap();
        }
        try {
            return Arrays.stream(query.split("&"))
                    .map(param -> {
                        String[] pair = param.split("=", 2);
                        if (pair.length == 2) {
                            try {
                                String key = URLDecoder.decode(pair[0], StandardCharsets.UTF_8.name());
                                String value = URLDecoder.decode(pair[1], StandardCharsets.UTF_8.name());
                                return new String[]{key, value};
                            } catch (UnsupportedEncodingException e) {
                                LOGGER_BASE_WORKER_HANDLER.log(Level.WARNING, "Error al decodificar parámetro (query): " + param, e);
                                return null;
                            }
                        }
                        LOGGER_BASE_WORKER_HANDLER.log(Level.WARNING, "Parámetro query malformado: " + param);
                        return null;
                    })
                    .filter(pair -> pair != null && pair[0] != null && !pair[0].isEmpty())
                    .collect(Collectors.toMap(pair -> pair[0], pair -> pair[1], (v1, v2) -> v1));
        } catch (Exception e) {
            LOGGER_BASE_WORKER_HANDLER.log(Level.WARNING, "Error general al parsear query params: " + query, e);
            return Collections.emptyMap();
        }
    }

    protected Map<String, String> parseFormData(HttpExchange exchange) throws IOException {
         if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            return Collections.emptyMap();
        }
        String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
        if (contentType == null || !contentType.toLowerCase().startsWith("application/x-www-form-urlencoded")) {
            LOGGER_BASE_WORKER_HANDLER.warning("Content-Type no es application/x-www-form-urlencoded para POST: " + contentType);
            return Collections.emptyMap();
        }
        try (java.io.InputStreamReader isr = new java.io.InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8);
             java.io.BufferedReader br = new java.io.BufferedReader(isr)) {
            String formDataString = br.lines().collect(Collectors.joining("\n"));
            return parseQueryParams(formDataString);
        } catch (Exception e) {
            LOGGER_BASE_WORKER_HANDLER.log(Level.WARNING, "Error al parsear form data", e);
            return Collections.emptyMap();
        }
    }

    protected void sendResponse(HttpExchange exchange, int statusCode, String responseBody) throws IOException {
        LOGGER_BASE_WORKER_HANDLER.log(Level.FINE, "Worker Handler Base: Enviando respuesta: {0} - {1} (hilo: {2})", 
            new Object[]{statusCode, responseBody, Thread.currentThread().getName()});
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8");
        byte[] responseBytes = responseBody.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, responseBytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(responseBytes);
        }
    }
}