const express = require('express');
const { Pool } = require('pg');

const app = express();
app.use(express.urlencoded({ extended: false }));

// Carga configuración de entorno
const PORT       = process.env.WORKER_PORT || 8082;
const WORKER_ID  = process.env.WORKER_ID  || 'js-worker';
const DB_HOST    = process.env.DB_HOST    || 'localhost';
const DB_PORT    = process.env.DB_PORT    || 5432;
const DB_USER    = process.env.DB_USER    || 'user_parcial';
const DB_PASS    = process.env.DB_PASSWORD|| 'pw_parcial';
const DB_NAME    = process.env.DB_NAME    || 'banco_distribuido';

// Pool de conexiones
const pool = new Pool({
  host:     DB_HOST,
  port:     DB_PORT,
  user:     DB_USER,
  password: DB_PASS,
  database: DB_NAME,
  max:      5,
  idleTimeoutMillis: 30000
});

app.get('/api/worker/saldo', async (req, res) => {
  const { cuentaId } = req.query;
  if (!cuentaId) {
    return res.status(400).send("Error: Parámetro 'cuentaId' es requerido.");
  }
  try {
    const { rows } = await pool.query(
      'SELECT SALDO FROM Cuenta WHERE ID_CUENTA = $1',
      [cuentaId]
    );
    if (rows.length === 0) {
      return res.status(404).send(`Error: Cuenta ${cuentaId} no encontrada en este nodo.`);
    }
    res.send(rows[0].saldo.toString());
  } catch (err) {
    console.error(`Worker [${WORKER_ID}] Saldo error:`, err);
    res.status(500).send('ERROR interno al consultar saldo.');
  }
});

app.post('/api/worker/transferir', async (req, res) => {
  const { cuentaOrigen, cuentaDestino, monto } = req.body;
  if (!cuentaOrigen || !cuentaDestino || !monto) {
    return res.status(400).send("Error: faltan parámetros.");
  }
  const client = await pool.connect();
  try {
    await client.query('BEGIN');
    // 1. verificar saldo origen
    const src = await client.query(
      'SELECT SALDO FROM Cuenta WHERE ID_CUENTA = $1 FOR UPDATE',
      [cuentaOrigen]
    );
    if (!src.rows.length || src.rows[0].saldo < parseFloat(monto)) {
      throw new Error('Saldo insuficiente o cuenta origen no existe');
    }
    // 2. debitar
    await client.query(
      'UPDATE Cuenta SET SALDO = SALDO - $1 WHERE ID_CUENTA = $2',
      [monto, cuentaOrigen]
    );
    // 3. acreditar
    const dest = await client.query(
      'SELECT SALDO FROM Cuenta WHERE ID_CUENTA = $1 FOR UPDATE',
      [cuentaDestino]
    );
    if (!dest.rows.length) {
      throw new Error('Cuenta destino no encontrada en este nodo');
    }
    await client.query(
      'UPDATE Cuenta SET SALDO = SALDO + $1 WHERE ID_CUENTA = $2',
      [monto, cuentaDestino]
    );
    // 4. registrar transacción
    const txnId = `TXN-${WORKER_ID}-${Date.now()}`;
    await client.query(
      `INSERT INTO Transacciones 
         (ID_TRANSACCION, ID_ORIGEN, ID_DESTINO, MONTO, FECHA_HORA, ESTADO)
       VALUES ($1,$2,$3,$4,NOW(),'CONFIRMADA')`,
      [txnId, cuentaOrigen, cuentaDestino, monto]
    );
    await client.query('COMMIT');
    res.send(`CONFIRMACIÓN: ${txnId}`);
  } catch (err) {
    await client.query('ROLLBACK');
    console.error(`Worker [${WORKER_ID}] Transferencia error:`, err);
    res.status(500).send(`ERROR: ${err.message}`);
  } finally {
    client.release();
  }
});

app.get('/api/worker/arqueoParcial', async (_, res) => {
  try {
    const { rows } = await pool.query('SELECT SUM(SALDO) AS total FROM Cuenta');
    res.send((rows[0].total||0).toString());
  } catch (err) {
    console.error(`Worker [${WORKER_ID}] Arqueo error:`, err);
    res.status(500).send('ERROR interno en arqueo');
  }
});

app.listen(PORT, () => {
  console.log(`JS Worker [${WORKER_ID}] escuchando en puerto ${PORT}`);
});
