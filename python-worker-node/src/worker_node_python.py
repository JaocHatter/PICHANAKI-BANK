# worker_node_python.py
import http.server
import socketserver
import json
import threading
import os
from urllib.parse import urlparse, parse_qs

# --- Configuration ---
# These would ideally be loaded from environment variables or a config file
DATA_DIR = "/app/data" # Example data directory inside the container
CUENTA_FILE = os.path.join(DATA_DIR, os.environ.get("CUENTA_FILENAME", "cuenta_p_r.txt"))
TRANSACCIONES_FILE = os.path.join(DATA_DIR, os.environ.get("TRANSACCIONES_FILENAME", "transacciones_p_r.txt"))

# Thread locks for file access
cuenta_lock = threading.Lock()
transacciones_lock = threading.Lock()

# --- Data Handling Functions ---
def get_saldo_from_file(id_cuenta):
    """
    Reads the cuenta.txt file and returns the saldo for the given id_cuenta.
    Assumes pipe-delimited format: ID_CUENTA|ID_CLIENTE|SALDO|TIPO_CUENTA
    """
    with cuenta_lock:
        try:
            with open(CUENTA_FILE, 'r') as f:
                for line in f:
                    parts = line.strip().split('|')
                    if parts[0] == str(id_cuenta):
                        return float(parts[2])
        except FileNotFoundError:
            print(f"Error: {CUENTA_FILE} not found.")
            return None
    return None # Account not found or error

def update_saldo_in_file(id_cuenta, nuevo_saldo):
    """
    Updates the saldo for a given id_cuenta in cuenta.txt.
    Returns True if successful, False otherwise.
    """
    lines = []
    updated = False
    with cuenta_lock:
        try:
            with open(CUENTA_FILE, 'r') as f:
                lines = f.readlines()
            
            with open(CUENTA_FILE, 'w') as f:
                for line in lines:
                    parts = line.strip().split('|')
                    if parts[0] == str(id_cuenta):
                        parts[2] = f"{nuevo_saldo:.2f}"
                        f.write("|".join(parts) + "\n")
                        updated = True
                    else:
                        f.write(line)
        except FileNotFoundError:
            print(f"Error: {CUENTA_FILE} not found during update.")
            return False
    return updated

def registrar_transaccion_in_file(id_origen, id_destino, monto, estado, fecha_hora):
    """
    Appends a new transaction to transacciones.txt.
    Format: ID_TRANSACC|ID_ORIGEN|ID_DESTINO|MONTO|FECHA_HORA|ESTADO
    (ID_TRANSACC would need a generation strategy)
    """
    # Simplified ID generation for example
    new_transacc_id = sum(1 for line in open(TRANSACCIONES_FILE)) + 1 if os.path.exists(TRANSACCIONES_FILE) else 1
    
    with transacciones_lock:
        try:
            with open(TRANSACCIONES_FILE, 'a') as f:
                f.write(f"{new_transacc_id}|{id_origen}|{id_destino}|{monto:.2f}|{fecha_hora}|{estado}\n")
            return True
        except FileNotFoundError:
            # Create file if not exists
             with open(TRANSACCIONES_FILE, 'w') as f:
                f.write(f"{new_transacc_id}|{id_origen}|{id_destino}|{monto:.2f}|{fecha_hora}|{estado}\n")
             return True
        except Exception as e:
            print(f"Error writing transaction: {e}")
            return False


# --- HTTP Request Handler ---
class WorkerHandler(http.server.BaseHTTPRequestHandler):
    def _send_response(self, status_code, data):
        self.send_response(status_code)
        self.send_header('Content-type', 'application/json')
        self.end_headers()
        self.wfile.write(json.dumps(data).encode('utf-8'))

    def do_GET(self):
        parsed_path = urlparse(self.path)
        query_params = parse_qs(parsed_path.query)

        if parsed_path.path == '/api/worker/health':
            self._send_response(200, {"status": "UP", "language": "Python"})
        
        elif parsed_path.path == '/api/worker/consultar_saldo':
            id_cuenta = query_params.get('ID_CUENTA', [None])[0]
            if not id_cuenta:
                self._send_response(400, {"error": "ID_CUENTA is required"})
                return
            
            saldo = get_saldo_from_file(id_cuenta)
            if saldo is not None:
                self._send_response(200, {"ID_CUENTA": id_cuenta, "SALDO": saldo})
            else:
                self._send_response(404, {"error": "Cuenta no encontrada o error al leer el archivo"})
        
        # Add /api/worker/arqueo_parcial handler here
        # This would likely sum all 'SALDO' in its CUENTA_FILE
        elif parsed_path.path == '/api/worker/arqueo_parcial':
            total_saldo_parcial = 0
            error_reading = False
            with cuenta_lock:
                try:
                    with open(CUENTA_FILE, 'r') as f:
                        for line in f:
                            parts = line.strip().split('|')
                            if len(parts) >= 3: # Check if line has enough parts
                                try:
                                    total_saldo_parcial += float(parts[2])
                                except ValueError:
                                    print(f"Warning: Could not parse saldo from line: {line.strip()}")
                                    # Decide how to handle parse errors: skip, error out, etc.
                except FileNotFoundError:
                    print(f"Error: {CUENTA_FILE} not found for arqueo.")
                    error_reading = True
                except Exception as e:
                    print(f"Error during arqueo: {e}")
                    error_reading = True
            
            if error_reading:
                 self._send_response(500, {"error": "Error al realizar arqueo parcial"})
            else:
                self._send_response(200, {"saldo_parcial_nodo": total_saldo_parcial})

        else:
            self._send_response(404, {"error": "Endpoint no encontrado"})

    def do_POST(self):
        parsed_path = urlparse(self.path)
        if parsed_path.path == '/api/worker/transferir_fondos':
            content_length = int(self.headers['Content-Length'])
            post_data = self.rfile.read(content_length)
            try:
                data = json.loads(post_data.decode('utf-8'))
            except json.JSONDecodeError:
                self._send_response(400, {"error": "Invalid JSON"})
                return

            id_origen = data.get('ID_CUENTA_ORIGEN')
            id_destino = data.get('ID_CUENTA_DESTINO') # Destination might be handled by another worker
            monto = float(data.get('MONTO', 0))
            # FECHA_HORA should ideally be passed by central server or generated consistently
            fecha_hora = data.get('FECHA_HORA', 'YYYY-MM-DD HH:MM:SS') 


            if not all([id_origen, id_destino, monto > 0]):
                self._send_response(400, {"error": "Parámetros inválidos"})
                return

            # --- Transaction Logic (Simplified for node handling its own accounts) ---
            # This worker is responsible for ID_ORIGEN if it's in its partition
            saldo_origen = get_saldo_from_file(id_origen)

            if saldo_origen is None:
                # This means the ID_ORIGEN account is not managed by this worker.
                # The central server should route this request to the correct worker.
                # For this example, we assume this worker IS responsible.
                # If it's not, it should indicate this, or the central server's routing handles it.
                self._send_response(404, {"error": f"Cuenta origen {id_origen} no encontrada en este nodo"})
                return

            if saldo_origen < monto:
                registrar_transaccion_in_file(id_origen, id_destino, monto, "Rechazada (Fondos Insuficientes)", fecha_hora)
                self._send_response(400, {"error": "Fondos insuficientes", "ID_TRANSACCION_ESTADO": "Rechazada"})
                return

            # Restar monto de cuenta origen (if this worker handles it)
            if not update_saldo_in_file(id_origen, saldo_origen - monto):
                 self._send_response(500, {"error": "Error al actualizar saldo origen"})
                 return
            
            # Note: Sumar monto a CUENTA_DESTINO might be handled by another worker.
            # The central server would coordinate this. This worker confirms its part.
            # For simplicity, we assume if the DESTINO account is also on this node, we'd update it.
            # saldo_destino = get_saldo_from_file(id_destino)
            # if saldo_destino is not None:
            #    update_saldo_in_file(id_destino, saldo_destino + monto)
            
            if registrar_transaccion_in_file(id_origen, id_destino, monto, "Confirmada", fecha_hora):
                self._send_response(200, {"message": "Transferencia (parte origen) confirmada", "ID_TRANSACCION_ESTADO": "Confirmada"})
            else:
                # Rollback attempt (add funds back to origin) - complex in distributed systems
                # update_saldo_in_file(id_origen, saldo_origen) # Simplified rollback
                self._send_response(500, {"error": "Error al registrar transacción, posible inconsistencia."})
        else:
            self._send_response(404, {"error": "Endpoint no encontrado"})

# --- Server Setup ---
PORT = 8082 # Choose a different port than Java workers (e.g. 8081)
if __name__ == "__main__":
    # Ensure data directory exists
    os.makedirs(DATA_DIR, exist_ok=True) 
    # Create dummy data files if they don't exist for testing
    if not os.path.exists(CUENTA_FILE):
        with open(CUENTA_FILE, "w") as f:
            f.write("1|1|1500.00|Ahorros\n") # Example from PDF
            f.write("2|2|3200.50|Corriente\n")
            f.write("3|1|100.00|Ahorros\n") # For testing transfers

    if not os.path.exists(TRANSACCIONES_FILE):
        with open(TRANSACCIONES_FILE, "w") as f:
            pass # Empty initially

    print(f"Python Worker Node starting on port {PORT}...")
    print(f"Managing cuenta file: {CUENTA_FILE}")
    print(f"Managing transacciones file: {TRANSACCIONES_FILE}")
    
    httpd = socketserver.ThreadingHTTPServer(("", PORT), WorkerHandler)
    try:
        httpd.serve_forever()
    except KeyboardInterrupt:
        pass
    httpd.server_close()
    print("Server stopped.")