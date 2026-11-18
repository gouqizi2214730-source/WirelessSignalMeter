const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const { DatabaseSync } = require("node:sqlite");

const root = path.resolve(__dirname, "../../../..");
const args = process.argv.slice(2);
const source = fs.readFileSync(args.includes("--source-stdin") ? 0 : path.join(root,
    "networksurvey/src/main/java/com/craxiom/networksurvey/logging/db/SurveyDatabase.java"), "utf8");
const body = source.match(/MIGRATION_14_15 = new Migration\(14, 15\)[\s\S]*?\n    \};/);
assert.ok(body, "Missing v14 -> v15 migration");
const statements = [...body[0].matchAll(/database\.execSQL\(([\s\S]*?)\);/g)].map((m) =>
    [...m[1].matchAll(/"(?:[^"\\]|\\.)*"/g)].map((s) => JSON.parse(s[0])).join(""));
assert.ok(statements.every((sql) => !/\bDROP\s+COLUMN\b/i.test(sql)),
    "Android 13 SQLite 3.32 cannot execute DROP COLUMN");
assert.equal(statements.length, 33, "Review extraction if migration statements change");
const fixture = fs.readFileSync(path.join(__dirname, "fixtures/survey-v14.sql"), "utf8");
const tables = ["gsm", "cdma", "umts", "lte", "nr", "wifi"].map((s) => `${s}_survey_records`);
const flags = new Set(["ocidUploaded", "beaconDbUploaded"]);
const schema = (db, table) => db.prepare(`PRAGMA table_info(${table})`).all()
    .map(({ cid, ...column }) => column);
const records = (db, table, columns) =>
    db.prepare(`SELECT ${columns.map((c) => c.name).join(",")} FROM ${table} ORDER BY id`).all();
const sample = (column, id) => {
    if (column.name === "id") return id;
    if (id === 2 && !column.notnull) return null;
    if (column.type === "TEXT") return `kept ' "${column.name}" \u6d4b\u91cf`;
    if (column.name === "nci") return 4294967299;
    return 0;
};

let room = null;
const roomArg = args.indexOf("--room-schema");
if (roomArg >= 0) {
    const generated = fs.readFileSync(args[roomArg + 1], "utf8");
    assert.match(generated, /RoomOpenDelegate\(15,/);
    room = new DatabaseSync(":memory:");
    const create = [...generated.matchAll(/connection\.execSQL\(("(?:[^"\\]|\\.)*")\)/g)]
        .map((m) => JSON.parse(m[1])).filter((sql) => /^CREATE (?:TABLE|(?:UNIQUE )?INDEX)/.test(sql));
    create.forEach((sql) => room.exec(sql));
    assert.ok(create.length >= 10, "Expected the full Room-generated schema");
}

for (const empty of [false, true]) {
    const db = new DatabaseSync(":memory:");
    db.exec("PRAGMA foreign_keys=ON");
    db.exec(fixture);
    const before = new Map();
    for (const table of tables) {
        const oldColumns = schema(db, table);
        const insert = db.prepare(`INSERT INTO ${table} VALUES(${oldColumns.map(() => "?").join(",")})`);
        for (const id of [1, 2, 900]) insert.run(...oldColumns.map((c) => sample(c, id)));
        db.exec(`DELETE FROM ${table} WHERE ${empty ? "1" : "id=900"}`);
        const kept = oldColumns.filter((c) => !flags.has(c.name));
        before.set(table, { columns: kept, rows: records(db, table, kept) });
    }
    db.exec("INSERT INTO watchlist_entry(id,uuid,label,enabled,createdAt,cooldownSeconds) "
        + "VALUES(7,'keep-uuid','keep-label',1,1000,30)");
    db.exec("INSERT INTO watchlist_hit(id,entryId,rssi,timestamp) VALUES(8,7,0,1001)");
    const watch = ["watchlist_entry", "watchlist_hit"].map((t) => db.prepare(`SELECT * FROM ${t}`).all());
    db.exec("BEGIN");
    statements.forEach((sql) => db.exec(sql));
    db.exec("PRAGMA user_version=15; COMMIT");
    for (const table of tables) {
        const { columns, rows } = before.get(table);
        assert.deepEqual(schema(db, table), columns, `${table}: schema changed beyond upload flags`);
        assert.deepEqual(records(db, table, columns), rows, `${table}: record data changed`);
        if (room) assert.deepEqual(schema(db, table), schema(room, table), `${table}: Room schema mismatch`);
        const nextColumns = columns.filter((c) => c.name !== "id");
        const result = db.prepare(`INSERT INTO ${table} (${nextColumns.map((c) => c.name).join(",")}) `
            + `VALUES(${nextColumns.map(() => "?").join(",")})`).run(...nextColumns.map((c) => sample(c, 3)));
        assert.equal(Number(result.lastInsertRowid), 901, `${table}: AUTOINCREMENT sequence regressed`);
    }
    for (const [i, table] of ["watchlist_entry", "watchlist_hit"].entries()) {
        assert.deepEqual(db.prepare(`SELECT * FROM ${table}`).all(), watch[i]);
        if (room) assert.deepEqual(schema(db, table), schema(room, table));
    }
    assert.equal(db.prepare("SELECT COUNT(*) AS n FROM sqlite_master WHERE name IN "
        + "('tower_cache','ns_analytics_queue','ns_analytics_connection')").get().n, 0);
    assert.deepEqual(db.prepare("PRAGMA foreign_key_check").all(), []);
    assert.equal(db.prepare("PRAGMA integrity_check").get().integrity_check, "ok");
    db.close();
    console.log(`PASS: ${empty ? "empty history" : "populated history"} v14 -> v15; `
        + "six record tables, NULL/zero/64-bit values, IDs, sequences and Watchlist preserved");
}
if (room) {
    room.close();
    console.log("PASS: migrated tables match the Room-generated v15 schema");
}
console.log("PASS: production migration SQL on host SQLite (not Android instrumentation)");
