import * as duckdb from '@duckdb/duckdb-wasm';
import { fetchParquetTable } from '../api';

let duckdbInstance: duckdb.AsyncDuckDB | null = null;
let duckdbConnection: duckdb.AsyncDuckDBConnection | null = null;
let initPromise: Promise<{ db: duckdb.AsyncDuckDB; conn: duckdb.AsyncDuckDBConnection }> | null = null;

export function isDuckDbReady(): boolean {
  return duckdbConnection !== null;
}

export async function initDuckDb(): Promise<{ db: duckdb.AsyncDuckDB; conn: duckdb.AsyncDuckDBConnection }> {
  if (duckdbConnection && duckdbInstance) {
    return { db: duckdbInstance, conn: duckdbConnection };
  }

  if (initPromise) {
    return initPromise;
  }

  initPromise = (async () => {
    try {
      const JSDELIVR_BUNDLES = duckdb.getJsDelivrBundles();
      const bundle = await duckdb.selectBundle(JSDELIVR_BUNDLES);

      const workerBlob = new Blob([`importScripts("${bundle.mainWorker!}");`], { type: 'text/javascript' });
      const workerUrl = URL.createObjectURL(workerBlob);

      const worker = new Worker(workerUrl);
      const logger = new duckdb.ConsoleLogger(duckdb.LogLevel.WARNING);
      const db = new duckdb.AsyncDuckDB(logger, worker);

      await db.instantiate(bundle.mainModule, bundle.pthreadWorker);
      URL.revokeObjectURL(workerUrl);

      const conn = await db.connect();
      duckdbInstance = db;
      duckdbConnection = conn;

      return { db, conn };
    } catch (err) {
      console.error('DuckDB-Wasm initialization failed:', err);
      initPromise = null;
      throw err;
    }
  })();

  return initPromise;
}

export async function queryDuckDb(sql: string): Promise<Record<string, any>[]> {
  const { conn } = await initDuckDb();
  const table = await conn.query(sql);
  const rows: Record<string, any>[] = [];
  for (let i = 0; i < table.numRows; i++) {
    const row: Record<string, any> = {};
    for (const field of table.schema.fields) {
      const col = table.getChild(field.name);
      if (col) {
        const val = col.get(i);
        row[field.name] = typeof val === 'bigint' ? Number(val) : val;
      }
    }
    rows.push(row);
  }
  return rows;
}

export async function loadParquetTable(tableName: string): Promise<void> {
  const { db, conn } = await initDuckDb();
  const buffer = await fetchParquetTable(tableName);
  await db.registerFileBuffer(`${tableName}.parquet`, new Uint8Array(buffer));
  await conn.query(`CREATE OR REPLACE VIEW ${tableName} AS SELECT * FROM read_parquet('${tableName}.parquet')`);
}
