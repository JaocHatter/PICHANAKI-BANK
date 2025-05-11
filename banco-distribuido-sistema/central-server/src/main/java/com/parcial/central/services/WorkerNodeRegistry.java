package com.parcial.central.services;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Registra y gestiona la información sobre los Nodos Trabajadores disponibles.
 * Incluye sus direcciones y las particiones de datos que manejan.
 * En un sistema real, esto podría integrarse con un servicio de descubrimiento.
 */
public class WorkerNodeRegistry {
    private static final Logger LOGGER = Logger.getLogger(WorkerNodeRegistry.class.getName());
    // Mapa de ID de nodo a información del nodo
    private final Map<String, WorkerNodeInfo> workerNodes = new ConcurrentHashMap<>();
    // Mapa de clave de partición a lista de nodos que la sirven (para réplicas)
    private final Map<String, List<WorkerNodeInfo>> partitionToNodesMap = new ConcurrentHashMap<>();

    public void addWorkerNode(String id, String address, String partitionsStr) {
        WorkerNodeInfo nodeInfo = new WorkerNodeInfo(id, address);
        workerNodes.put(id, nodeInfo);
        String[] partitionKeys = partitionsStr.split(",");
        for (String pk : partitionKeys) {
            partitionToNodesMap.computeIfAbsent(pk.trim(), k -> new ArrayList<>()).add(nodeInfo);
            nodeInfo.addManagedPartition(pk.trim());
        }
        LOGGER.info("Nodo trabajador registrado: " + id + " en " + address + " manejando particiones: " + partitionsStr);
    }

    public List<WorkerNodeInfo> getNodesForPartition(String partitionKey) {
        return partitionToNodesMap.getOrDefault(partitionKey, Collections.emptyList());
    }

    public List<WorkerNodeInfo> getAllWorkerNodes() {
        return new ArrayList<>(workerNodes.values());
    }
    
    /**
     * Determina la clave de partición para una cuenta dada.
     * Esta es una lógica de ejemplo; en un sistema real, sería más robusta.
     * Asumimos 2 particiones para cada tipo de tabla (Cliente, Cuenta, Transacciones)
     * y que la partición de cuenta determina las otras.
     */
    public String getPartitionKeyForAccount(String accountId) {
        // Lógica de ejemplo: si el ID de cuenta es numérico, par o impar para 2 particiones
        try {
            long numericId = Long.parseLong(accountId.replaceAll("[^0-9]", ""));
            if (numericId % 2 == 0) {
                return "Cuenta-P2"; // O cualquier clave que uses para la partición 2 de Cuentas
            } else {
                return "Cuenta-P1"; // Clave para la partición 1 de Cuentas
            }
        } catch (NumberFormatException e) {
            // Fallback o una estrategia de hash más consistente
            return "Cuenta-P" + (Math.abs(accountId.hashCode() % 2) + 1);
        }
    }
}