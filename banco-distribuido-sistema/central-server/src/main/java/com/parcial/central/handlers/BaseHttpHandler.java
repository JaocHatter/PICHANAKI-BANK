package com.parcial.central.handlers;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
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
 * Clase base abstracta para los manejadores HTTP del Servidor Central.
 * Proporciona utilidades comunes como parseo de query params y envío de respuestas.
 */
public abstract class BaseHttpHandler implements HttpHandler {
    private static final Logger LOGGER_BASE_HANDLER = Logger.getLogger(BaseHttpHandler.class.getName());


    @Override
    public void handle(HttpExchange exchange) throws IOException {
        // Este método será implementado por las subclases específicas
        // pero podrían compartir lógica aquí si fuera necesario antes de delegar.
        // Por ahora, las subclases lo implementan directamente.
    }

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
                                LOGGER_BASE_HANDLER.log(Level.WARNING, "Error al decodificar parámetro (query): " + param, e);
                                return null;
                            }
                        }
                        LOGGER_BASE_HANDLER.log(Level.WARNING, "Parámetro query malformado: " + param);
                        return null;
                    })
                    .filter(pair -> pair != null && pair[0] != null && !pair[0].isEmpty())
                    .collect(Collectors.toMap(pair -> pair[0], pair -> pair[1], (v1, v2) -> v1)); // Toma la primera si hay duplicados
        } catch (Exception e) {
            LOGGER_BASE_HANDLER.log(Level.WARNING, "Error general al parsear query params: " + query, e);
            return Collections.emptyMap();
        }
    }

    protected Map<String, String> parseFormData(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            return Collections.emptyMap();
        }
        String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
        if (contentType == null || !contentType.toLowerCase().startsWith("application/x-www-form-urlencoded")) {
            LOGGER_BASE_HANDLER.warning("Content-Type no es application/x-www-form-urlencoded para POST: " + contentType);
            return Collections.emptyMap();
        }

        try (java.io.InputStreamReader isr = new java.io.InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8);
             java.io.BufferedReader br = new java.io.BufferedReader(isr)) {
            String formDataString = br.lines().collect(Collectors.joining("\n"));
            return parseQueryParams(formDataString); // Reutiliza el parser de query params
        } catch (Exception e) {
            LOGGER_BASE_HANDLER.log(Level.WARNING, "Error al parsear form data", e);
            return Collections.emptyMap();
        }
    }
    
    protected void sendResponse(HttpExchange exchange, int statusCode, String responseBody) throws IOException {
        // Asegurar que la respuesta se envía en el hilo correcto (el del handler)
        LOGGER_BASE_HANDLER.log(Level.FINE, "Enviando respuesta: {0} - {1} (hilo: {2})", 
            new Object[]{statusCode, responseBody, Thread.currentThread().getName()});
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8");
        byte[] responseBytes = responseBody.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, responseBytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(responseBytes);
        }
    }

    protected void sendErrorResponse(HttpExchange exchange, int statusCode, String errorMessage) throws IOException {
        LOGGER_BASE_HANDLER.log(Level.WARNING, "Enviando error {0}: {1} (hilo: {2})", 
            new Object[]{statusCode, errorMessage, Thread.currentThread().getName()});
        sendResponse(exchange, statusCode, "Error: " + errorMessage);
    }
}