const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const { DatabaseSync } = require("node:sqlite");

const root = path.resolve(__dirname, "../../../..");
const main = path.join(root, "networksurvey/src/main/java/com/craxiom/networksurvey");
const source = (name) => fs.readFileSync(path.join(main, name), "utf8");
const javaStrings = (text) => [...text.matchAll(/"(?:[^"\\]|\\.)*"/g)].map((m) => JSON.parse(m[0])).join("");
const map = source("db/MapPointDbHelper.java");
const speed = source("db/SpeedTestDbHelper.java");
const tasks = source("db/SurveyTaskStore.kt");
const mapCreate = javaStrings(map.match(/String CREATE_SQL\s*=([\s\S]*?);/)[1]);
const mapAdds = [...map.matchAll(/db\.execSQL\("(ALTER TABLE map_point[^"]*)"\)/g)].map((m) => m[1]);
assert.equal(mapAdds.length, 8, "Review migration extraction when the schema changes");

let count = 0;
for (const version of [1, 2]) {
    const db = new DatabaseSync(":memory:");
    const legacyColumns = mapCreate.slice(0, mapCreate.indexOf(", task_id"));
    const oldCreate = (version === 1 ? legacyColumns.replace(", sim_slot INTEGER NOT NULL DEFAULT -1", "") : legacyColumns) + ")";
    db.exec(oldCreate);
    db.exec("INSERT INTO map_point(latitude,longitude,sinr,remark,timestamp) VALUES(30,100,0,'keep',1795000000000)");
    if (version === 1) db.exec("ALTER TABLE map_point ADD COLUMN sim_slot INTEGER NOT NULL DEFAULT -1");
    mapAdds.forEach((sql) => db.exec(sql));
    const row = db.prepare("SELECT * FROM map_point").get();
    assert.equal(row.remark, "keep");
    assert.equal(row.sinr, 0);
    assert.equal(row.task_id, null);
    assert.equal(row.subscription_id, -1);
    assert.equal(row.location_accuracy_m, -1);
    assert.equal(row.clock_id, null);
    const fresh = new DatabaseSync(":memory:");
    fresh.exec(mapCreate);
    const columns = (d) => d.prepare("PRAGMA table_info(map_point)").all().map((r) => [r.name, r.type, r.notnull, r.dflt_value]);
    assert.deepEqual(columns(db), columns(fresh));
    console.log(`PASS: map v${version} -> v3 preserves rows, zero SINR and unknown task`);
    count++;
}

{
    const db = new DatabaseSync(":memory:");
    const create = javaStrings(speed.match(/onCreate\(SQLiteDatabase db\)\s*\{\s*db\.execSQL\(([\s\S]*?)\);/)[1]);
    db.exec(create.replace(", task_id TEXT", ""));
    db.exec("INSERT INTO speed_records(time,download_speed,server_type) VALUES(1795000000000,12345,'HTTP')");
    const upgrade = speed.match(/onUpgrade[\s\S]*?db\.execSQL\("([^"]+)"\)/)[1];
    db.exec(upgrade);
    assert.equal(db.prepare("SELECT download_speed FROM speed_records").get().download_speed, 12345);
    assert.equal(db.prepare("SELECT task_id FROM speed_records").get().task_id, null);
    assert.ok(!speed.includes("DROP TABLE"));
    console.log("PASS: speed v1 -> v2 preserves history");
    count++;
}

{
    const db = new DatabaseSync(":memory:");
    const createBody = tasks.match(/override fun onCreate[\s\S]*?\n    \}/)[0];
    const statements = [...createBody.matchAll(/db\.execSQL\((?:"""([\s\S]*?)"""|("(?:[^"\\]|\\.)*"))\)/g)]
        .map((m) => m[1] ?? JSON.parse(m[2]));
    assert.equal(statements.length, 4);
    statements.forEach((sql) => db.exec(sql));
    const addTask = db.prepare("INSERT INTO survey_task(id,name,started_at,started_elapsed_ms,clock_id) VALUES(?,?,?,?,?)");
    addTask.run("old", "old", 1000, 100, "clock");
    const addEvent = db.prepare(`INSERT INTO survey_event
        (id,task_id,time,elapsed_ms,clock_id,source,kind,title,detail,time_basis)
        VALUES(?,?,?,?,?,?,?,?,?,?)`);
    addEvent.run("event", "old", 1500, 600, "clock", "speedtest", "speed_result", "result", "", "received");
    db.exec("BEGIN");
    db.prepare("UPDATE survey_task SET ended_at=? WHERE id=?").run(2000, "old");
    addTask.run("new", "new", 2000, 1100, "clock");
    db.exec("COMMIT");
    // An in-flight request still appends to its captured task after the user starts a new one.
    addEvent.run("late", "old", 2500, 1600, "clock", "speedtest", "speed_result", "late", "", "received");
    assert.equal(db.prepare("SELECT COUNT(*) AS n FROM survey_event WHERE task_id='old'").get().n, 2);
    assert.equal(db.prepare("SELECT id FROM survey_task WHERE ended_at IS NULL").get().id, "new");
    const bind = db.prepare("INSERT OR IGNORE INTO task_mission(task_id,mission_id) VALUES(?,?)");
    bind.run("old", "mission");
    bind.run("old", "mission");
    bind.run("new", "mission");
    assert.equal(db.prepare("SELECT COUNT(*) AS n FROM task_mission").get().n, 2);
    assert.equal(db.prepare("SELECT id FROM survey_event WHERE task_id=? ORDER BY time DESC,rowid DESC LIMIT 1").get("old").id, "late");
    console.log("PASS: production task/event schema supports isolation, late completion and Mission aliases");
    count++;
}
console.log(`PASS: ${count} SQLite schema scenarios (host SQLite; not Android instrumentation)`);
