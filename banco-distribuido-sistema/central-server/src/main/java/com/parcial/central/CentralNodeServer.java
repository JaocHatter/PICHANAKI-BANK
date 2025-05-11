package com.parcial.central;

import com.sun.net.httpserver.HttpServer;

import com.parcial.central.handlers.ArqueoHandler; 
import com.parcial.central.handlers.ConsultarSaldoHandler;
import com.parcial.central.handlers.TransferirFondosHandler;

import com.parcial.central.services.WorkerNodeClient;
import com.parcial.central.services.WorkerNodeRegistry;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Servidor Central del sistema bancario distribuido.
 * Recibe solicitudes de clientes, las coordina con Nodos Trabajadores
 * y gestiona la concurrencia mediante un ThreadPoolExecutor.
 */
public class CentralNodeServer {

    private static final Logger LOGGER = Logger.getLogger(CentralNodeServer.class.getName());
    private static final int DEFAULT_PORT = 8000;
    // Configuración del ThreadPool para manejar las solicitudes HTTP
    private static final int CORE_POOL_SIZE = 10; // Hilos base
    private static final int MAX_POOL_SIZE = 20;  // Hilos máximos
    private static final long KEEP_ALIVE_TIME = 60L; // Tiempo que un hilo extra puede estar inactivo
    private final int port;
    private HttpServer server;
    private final WorkerNodeRegistry workerNodeRegistry;
    private final WorkerNodeClient workerNodeClient;
    private final ThreadPoolExecutor requestHandlerThreadPool; // Pool para los handlers HTTP

    public CentralNodeServer(int port) {
        this.port = port;
        this.workerNodeRegistry = new WorkerNodeRegistry(); // Cargar configuración de workers
        // Pool de hilos dedicado para las llamadas salientes a los workers
        ThreadPoolExecutor outgoingRequestsExecutor = new ThreadPoolExecutor(
            5, 10, 60L, TimeUnit.SECONDS, new LinkedBlockingQueue<>()
        );
        this.workerNodeClient = new WorkerNodeClient(outgoingRequestsExecutor);


        // Simulación de registro de Nodos Trabajadores (esto vendría de una config o descubrimiento)
        // Los puertos 8081 son para las APIs de los workers, no para sus BDs.
        workerNodeRegistry.addWorkerNode("worker-db-0", "http://worker-db-0.worker-db-svc.default.svc.cluster.local:8081", "Cliente-P1,Cuenta-P1,Transacciones-P1");
        workerNodeRegistry.addWorkerNode("worker-db-1", "http://worker-db-1.worker-db-svc.default.svc.cluster.local:8081", "Cliente-P1,Cuenta-P2,Transacciones-P2");
        workerNodeRegistry.addWorkerNode("worker-db-2", "http://worker-db-1.worker-db-svc.default.svc.cluster.local:8081", "Cliente-P1,Cuenta-P1,Transacciones-P1");
        workerNodeRegistry.addWorkerNode("worker-db-3", "http://worker-db-1.worker-db-svc.default.svc.cluster.local:8081", "Cliente-P2,Cuenta-P2,Transacciones-P2");
        workerNodeRegistry.addWorkerNode("worker-db-4", "http://worker-db-1.worker-db-svc.default.svc.cluster.local:8081", "Cliente-P2,Cuenta-P1,Transacciones-P1");
        workerNodeRegistry.addWorkerNode("worker-db-5", "http://worker-db-5.worker-db-svc.default.svc.cluster.local:8081", "Cliente-P2,Cuenta-P2,Transacciones-P2");

        // ThreadPool para manejar las solicitudes HTTP entrantes
        this.requestHandlerThreadPool = new ThreadPoolExecutor(
                CORE_POOL_SIZE,
                MAX_POOL_SIZE,
                KEEP_ALIVE_TIME,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>() 
        );
        LOGGER.info("ThreadPool para Handlers HTTP inicializado con " + CORE_POOL_SIZE + " hilos base y " + MAX_POOL_SIZE + " máximos.");
    }

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);

        // Asignar el ThreadPoolExecutor al servidor HTTP
        // Cada solicitud HTTP será procesada por un hilo de este pool
        server.setExecutor(this.requestHandlerThreadPool);

        // Configurar contextos (rutas de la API)
        // Cada handler usará el WorkerNodeClient (que tiene su propio pool para llamadas salientes)
        server.createContext("/api/saldo", new ConsultarSaldoHandler(workerNodeRegistry, workerNodeClient));
        server.createContext("/api/transferencia", new TransferirFondosHandler(workerNodeRegistry, workerNodeClient));
        server.createContext("/api/arqueo", new ArqueoHandler(workerNodeRegistry, workerNodeClient));

        server.start();
        LOGGER.log(Level.INFO, "Servidor Central iniciado en el puerto: {0}", port);
        LOGGER.log(Level.INFO, "Usando ThreadPool para manejar hasta {0} solicitudes concurrentes.", MAX_POOL_SIZE);
    }

    public void stop(int delay) {
        if (server != null) {
            LOGGER.info("Deteniendo Servidor Central...");
            server.stop(delay);
        }
        if (requestHandlerThreadPool != null) {
            requestHandlerThreadPool.shutdown();
            try {
                if (!requestHandlerThreadPool.awaitTermination(60, TimeUnit.SECONDS)) {
                    requestHandlerThreadPool.shutdownNow();
                }
            } catch (InterruptedException ie) {
                requestHandlerThreadPool.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        if (workerNodeClient != null) {
            workerNodeClient.shutdown(); // Asegurar que el pool del cliente también se cierre
        }
        LOGGER.info("Servidor Central detenido.");
    }

    public static void main(String[] args) {
        int serverPort = args.length > 0 ? Integer.parseInt(args[0]) : DEFAULT_PORT;
        CentralNodeServer centralNode = new CentralNodeServer(serverPort);
        try {
            centralNode.start();
            Runtime.getRuntime().addShutdownHook(new Thread(() -> centralNode.stop(0)));
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error al iniciar el Servidor Central", e);
        }
    }
}
