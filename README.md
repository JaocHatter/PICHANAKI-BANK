# Parcial 2025-I
## Escuela de Ciencia de la Computación
### CC4P1 Programación Concurrente y Distribuida

**Participantes:** 
- Jared Orihuela
- Mitchel Soto
- Yoel Mantari


Llamaremos a nuestros Pods worker-db-0 a worker-db-5.

    worker-db-0: Cliente-P1 (R1), Cuenta-P1 (R1), Transacciones-P1 (R1)
    worker-db-1: Cliente-P1 (R2), Cuenta-P2 (R1), Transacciones-P2 (R1)
    worker-db-2: Cliente-P1 (R3), Cuenta-P1 (R2), Transacciones-P1 (R2)
    worker-db-3: Cliente-P2 (R1), Cuenta-P2 (R2), Transacciones-P2 (R2)
    worker-db-4: Cliente-P2 (R2), Cuenta-P1 (R3), Transacciones-P1 (R3)
    worker-db-5: Cliente-P2 (R3), Cuenta-P2 (R3), Transacciones-P2 (R3)

## Pasos para levantar los NodosTrabajadores
```bash
kubectl create namespace banco

kubectl apply -f cm-worker-db-0-init.yaml \
kubectl apply -f cm-worker-db-1-init.yaml \
kubectl apply -f cm-worker-db-2-init.yaml \
kubectl apply -f cm-worker-db-3-init.yaml \
kubectl apply -f cm-worker-db-4-init.yaml \
kubectl apply -f cm-worker-db-5-init.yaml

kubectl apply -f db-statefulset.yaml

# Esperar unos minutos y...
kubectl get pods -n banco
```
## Levantar Servidor Central
```bash
kubectl apply -f central-server-deployment.yaml
```

Las operaciones serán exitosas si ves los pods en status: "RUNNING"


# Banco Distribuido

```bash
banco-distribuido-sistema/  
├── pom.xml                   
├── central-server/           
│   ├── pom.xml               
│   └── src/
│       └── main/
│           └── java/
│               └── com/
│                   └── parcial/
│                       └── central/
│                           ├── CentralNodeServer.java
│                           ├── handlers/
│                           │   ├── ConsultarSaldoHandler.java
│                           │   ├── TransferirFondosHandler.java
│                           │   ├── ArqueoHandler.java
│                           │   └── BaseHttpHandler.java
│                           └── services/
│                               ├── WorkerNodeRegistry.java
│                               └── WorkerNodeClient.java
└── worker-node/              
    ├── pom.xml               
    └── src/
        └── main/
            └── java/
                └── com/
                    └── parcial/
                        └── worker/
                            ├── WorkerNodeServer.java
                            ├── handlers/
                            │   ├── WorkerSaldoHandler.java
                            │   ├── WorkerTransferirHandler.java
                            │   ├── WorkerArqueoParcialHandler.java
                            │   └── BaseWorkerHandler.java
                            └── persistence/
                                ├── DatabaseManager.java
                                └── dao/
                                    ├── CuentaDAO.java
                                    └── TransaccionDAO.java
└── manifest
    └── lp_java
        └── central-server-deployment.yml
        └── db-statefulset.yml
    ├── cm-worker-db-0init.yaml
    ├── cm-worker-db-1-init.yaml
    ├── cm-worker-db-2-init.yaml
    ├── cm-worker-db-3-init.yaml
    ├── cm-worker-db-4-init.yaml
    ├── cm-worker-db-5-init.yaml
└── schemas
    ├── Cliente.sqñ
    ├── Cuenta.sql
    ├── Transacciones.sql
└── .gitignore
└── README.md
```

## Verificar que el nodo central se levantó correctamente

```bash
kubectl exec -it central-server-xxxxxxxx-xxxxx -n banco -c central-server-container -- /bin/sh
```

## Verificar despliegue de base de datos

```bash
kubectl exec -it worker-db-x -n banco -- bash

# Una vez dentro de la terminal de contenedor de postgres
psql -U user_parcial -d banco_distribuido
```

## Verificar logs del nodo central y nodos trabajadores

```bash 
kubectl logs worker-db-0 -n banco
kubectl logs central-server-6d6ff6d545-rghss -n banco
```

## Obtener endpoints  
```bash
# Copy the INTERNAL-IP
kubectl get nodes -o wide

# Obtener el puerto del service 
#Busca en la salida la columna PORT(S). Verás algo como 80:ZZZZZ/TCP, donde ZZZZZ es tu NodePort.
kubectl get svc -n banco
```
#### Endpoints del Nodo Central 
- /api/saldo
- /api/transferencia
- /api/arqueo

##### Consulta 
```bash
# Reemplaza <IP-DEL-NODO>, <NodePort_ZZZZZ> y <ID_DE_CUENTA_REAL> con tus valores
curl "http://<IP-DEL-NODO>:<NodePort_ZZZZZ>/api/saldo?cuentaId=<ID_DE_CUENTA_REAL>"
```

##### Transferencia
```bash
# Reemplaza <IP-DEL-NODO>, <NodePort_ZZZZZ>, <ID_CUENTA_ORIGEN>, <ID_CUENTA_DESTINO> y <MONTO>
curl -X POST \
  -H "Content-Type: application/json" \
  -d '{
        "cuentaOrigen": "<ID_CUENTA_ORIGEN>",
        "cuentaDestino": "<ID_CUENTA_DESTINO>",
        "monto": <MONTO_A_TRANSFERIR>
      }' \
  "http://<IP-DEL-NODO>:<NodePort_ZZZZZ>/api/transferencia"
```

##### Arqueo
```bash
# Reemplaza <IP-DEL-NODO> y <NodePort_ZZZZZ>
curl "http://<IP-DEL-NODO>:<NodePort_ZZZZZ>/api/arqueo"
```

## TODO

Añadir Probes para los nodos trabajadores

```yaml
readinessProbe:
          httpGet:
            path: /api/worker/health 
            port: 8081
          initialDelaySeconds: 15
          periodSeconds: 10
        livenessProbe:
          httpGet:
            path: /api/worker/health
            port: 8081
          initialDelaySeconds: 30
          periodSeconds: 20
```

De igual manera para el Server Central

```yaml
readinessProbe:
          httpGet:
            path: /api/health # Necesitarías un endpoint de health en tu CentralNodeServer
            port: 8000
          initialDelaySeconds: 10
          periodSeconds: 5
```