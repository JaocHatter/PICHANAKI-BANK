package com.parcial.worker;

import com.sun.net.httpserver.HttpServer;
import com.parcial.worker.handlers.*; // Asumiendo handlers del worker
import com.parcial.worker.persistence.DatabaseManager;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Servidor para un Nodo Trabajador. Escucha solicitudes del Servidor Central
 * y opera sobre su base de datos PostgreSQL local.
 * Utiliza un ThreadPoolExecutor para manejar la concurrencia.
 */
public class WorkerNodeServer {
    private static final Logger LOGGER = Logger.getLogger(WorkerNodeServer.class.getName());

    // Configuración del ThreadPool para el servidor HTTP del worker
    private static final int CORE_POOL_SIZE = 5; // Ajustable por worker
    private static final int MAX_POOL_SIZE = 10;
    private static final long KEEP_ALIVE_TIME = 60L;
    
    private final int port;
    private final String workerId;
    private HttpServer server;
    private final DatabaseManager dbManager;
    private final ThreadPoolExecutor requestHandlerThreadPool;

    public WorkerNodeServer(int port, String workerId, String dbUrl, String dbUser, String dbPassword) {
        this.port = port;
        this.workerId = workerId;
        // Inicializar el gestor de la base de datos
        this.dbManager = new DatabaseManager(dbUrl, dbUser, dbPassword); 
        LOGGER.info("Worker [" + workerId + "] conectado a DB: " + dbUrl);

        this.requestHandlerThreadPool = new ThreadPoolExecutor(
                CORE_POOL_SIZE,
                MAX_POOL_SIZE,
                KEEP_ALIVE_TIME,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>()
        );
        LOGGER.info("Worker [" + workerId + "] ThreadPool para Handlers HTTP inicializado.");
    }

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.setExecutor(this.requestHandlerThreadPool); // Concurrencia con ThreadPool

        // Contextos/endpoints específicos del worker
        server.createContext("/api/worker/saldo", new WorkerSaldoHandler(dbManager, workerId));
        server.createContext("/api/worker/transferir", new WorkerTransferirHandler(dbManager, workerId));
        server.createContext("/api/worker/arqueoParcial", new WorkerArqueoParcialHandler(dbManager, workerId));
        // Podría tener un endpoint de health check /api/worker/health

        server.start();
        LOGGER.log(Level.INFO, "Nodo Trabajador [{0}] iniciado en el puerto: {1}", new Object[]{workerId, port});
        LOGGER.log(Level.INFO, "Worker [{0}] usando ThreadPool para manejar hasta {1} solicitudes concurrentes.", new Object[]{workerId, MAX_POOL_SIZE});
    }

    public void stop(int delay) {
        LOGGER.info("Deteniendo Nodo Trabajador [" + workerId + "]...");
        if (server != null) {
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
        // dbManager.closeDataSource(); // Si usaras un pool de conexiones como HikariCP
        LOGGER.info("Nodo Trabajador [" + workerId + "] detenido.");
    }

    public static void main(String[] args) {
        int workerPort = Integer.parseInt(System.getenv().getOrDefault("WORKER_PORT", "8081"));
        String workerId = System.getenv().getOrDefault("WORKER_ID", "default-worker");
        
        // Dentro del Pod, la BD está en localhost y el puerto estándar de Postgres es 5432
        String dbHost = System.getenv().getOrDefault("DB_HOST", "localhost"); // Será localhost
        String dbPort = System.getenv().getOrDefault("DB_PORT", "5432");
        String dbName = System.getenv().getOrDefault("DB_NAME", "banco_distribuido");
        String dbUser = System.getenv().getOrDefault("DB_USER", "user_parcial");
        // La contraseña DEBE venir de un Secret de Kubernetes montado como variable de entorno o archivo
        String dbPassword = System.getenv().getOrDefault("DB_PASSWORD", "pw_parcial"); 
        if (dbPassword == null) {
            LOGGER.severe("Error: La variable de entorno DB_PASSWORD no está configurada.");
            System.exit(1);
        }

        String dbUrl = "jdbc:postgresql://" + dbHost + ":" + dbPort + "/" + dbName;

        WorkerNodeServer workerNode = new WorkerNodeServer(workerPort, workerId, dbUrl, dbUser, dbPassword);
        try {
            workerNode.start();
            Runtime.getRuntime().addShutdownHook(new Thread(() -> workerNode.stop(0)));
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error al iniciar el Nodo Trabajador [" + workerId + "]", e);
        }
    }
}