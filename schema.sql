
PRAGMA foreign_keys = ON;

CREATE TABLE IF NOT EXISTS ration_shops (
    shop_id TEXT PRIMARY KEY,
    shop_name TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS families (
    family_id TEXT PRIMARY KEY,
    ration_card_number TEXT NOT NULL UNIQUE,
    shop_id TEXT NOT NULL REFERENCES ration_shops(shop_id)
);

CREATE TABLE IF NOT EXISTS beneficiaries (
    beneficiary_id INTEGER PRIMARY KEY AUTOINCREMENT,
    family_id TEXT NOT NULL REFERENCES families(family_id),
    name TEXT NOT NULL,
    age INTEGER,
    government_employee INTEGER NOT NULL DEFAULT 0,
    active INTEGER NOT NULL DEFAULT 1
);

CREATE TABLE IF NOT EXISTS transactions (
    transaction_id TEXT PRIMARY KEY,
    family_id TEXT NOT NULL REFERENCES families(family_id),
    shop_id TEXT NOT NULL REFERENCES ration_shops(shop_id),
    quantity REAL NOT NULL,
    price_per_kg REAL NOT NULL,
    total_amount REAL NOT NULL,
    transaction_date TEXT NOT NULL,
    risk_score INTEGER DEFAULT 0,
    risk_level TEXT DEFAULT 'NORMAL',
    rice_quantity REAL DEFAULT 0,
    jowar_quantity REAL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS stock_records (
    stock_id INTEGER PRIMARY KEY AUTOINCREMENT,
    shop_id TEXT NOT NULL REFERENCES ration_shops(shop_id),
    item_name TEXT NOT NULL,
    quantity_received REAL NOT NULL DEFAULT 0,
    quantity_distributed REAL NOT NULL DEFAULT 0,
    record_date TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS ration_blocks (
    block_id INTEGER PRIMARY KEY AUTOINCREMENT,
    family_id TEXT NOT NULL REFERENCES families(family_id),
    blocked_until TEXT NOT NULL,
    reason TEXT
);
