#include "../../main/cpp/band_lock_protocol.h"

#define O_RDONLY 0
#define O_WRONLY 1
#define O_CREAT 2
#define O_EXCL 4
#define O_NOFOLLOW 8
#define O_CLOEXEC 16
#define EEXIST 17
#define CHECK(condition) do { if (!(condition)) return __LINE__; } while (0)

static int state_dir = 100, errno;
static int stored, read_error, open_error, write_error, file_sync_error, dir_sync_error;
static int reads, creates, writes, file_syncs, dir_syncs, unlinks, cm, efs, restarts, marker;
static uint8_t saved[64];

void *memset(void *target, int value, size_t size) {
    volatile uint8_t *p = target;
    for (size_t i = 0; i < size; i++) p[i] = (uint8_t)value;
    return target;
}

static void reset(void) {
    stored = read_error = open_error = write_error = file_sync_error = dir_sync_error = 0;
    reads = creates = writes = file_syncs = dir_syncs = unlinks = cm = efs = restarts = marker = 0;
    memset(saved, 0, sizeof(saved));
}

static int fail(const char *message) { (void)message; return 1; }
static int puts(const char *message) { (void)message; return 0; }
static int printf(const char *format, ...) { (void)format; return 0; }
static int read_mask(const char *path, int bytes, uint8_t *mask) {
    (void)path;
    reads++;
    if (read_error) return 1;
    for (int i = 0; i < bytes; i++) mask[i] = (uint8_t)(i + 1);
    return 0;
}
/* nr_nsa_present() probes through read_mask_to, not read_mask. */
static int read_mask_to(const char *path, int bytes, uint8_t *mask,
        int mask_cap, int timeout_ms) {
    (void)path; (void)mask_cap; (void)timeout_ms;
    reads++;
    if (read_error) return 1;
    for (int i = 0; i < bytes; i++) mask[i] = (uint8_t)(i + 1);
    return 0;
}
static int restore_mask(const char *name, int bytes, uint8_t *mask) {
    (void)name;
    if (!stored) return 1;
    for (int i = 0; i < bytes; i++) mask[i] = i == 0 ? 0x10 : 0;
    return 0;
}
static int openat(int dir, const char *name, int flags, ...) {
    (void)dir;
    if (flags == O_RDONLY) return marker ? 5 : -1;
    creates++;
    if (open_error) return -1;
    if (name[0] == 'n' && name[3] == 'n') {
        if (marker) { errno = EEXIST; return -1; }
        marker = 1;
    }
    return 5;
}
static long long write(int fd, const void *data, size_t bytes) {
    (void)fd;
    writes++;
    const uint8_t *p = data;
    for (size_t i = 0; i < bytes; i++) saved[i] = p[i];
    return write_error ? (long long)bytes - 1 : (long long)bytes;
}
static int fsync(int fd) {
    if (fd == state_dir) { dir_syncs++; return dir_sync_error; }
    file_syncs++;
    return file_sync_error;
}
static int close(int fd) { (void)fd; return 0; }
static int unlinkat(int dir, const char *name, int flags) {
    (void)dir; (void)name; (void)flags; unlinks++; return 0;
}
static int put_file(const char *dir, const char *file, const char *sync,
        const uint8_t *data, int bytes, uint8_t a, uint8_t b, int lte) {
    (void)dir; (void)file; (void)sync; (void)data; (void)bytes; (void)a; (void)b; (void)lte;
    efs++;
    return 0;
}
static int exchange(const uint8_t *data, int bytes, int kind, uint32_t tag, int a, int b) {
    (void)data; (void)bytes; (void)kind; (void)tag; (void)a; (void)b;
    cm++;
    return 0;
}
static int remove_file(const char *path, const char *dir) { (void)path; (void)dir; return 0; }
static int reboot_modem(void) { restarts++; return 0; }

/* The runner generates this include from the actual production functions. */
#include "band_lock_backup_under_test.inc"

__declspec(dllexport) int test_band_lock_backup(void) {
    reset();
    CHECK(lte_band_command("5", 0) == 0);
    CHECK(reads == 1 && creates == 1 && writes == 1 && file_syncs == 1 && dir_syncs == 1);
    CHECK(cm == 1 && efs == 0);
    for (int i = 0; i < 8; i++) CHECK(saved[i] == i + 1);

    reset(); read_error = 1;
    CHECK(lte_band_command("5", 0) == 0 && creates == 0 && cm == 1 && efs == 0);

    reset(); read_error = 1;
    CHECK(lte_band_command("41", 0) != 0 && creates == 0 && cm == 0 && efs == 0);

    reset(); open_error = 1;
    CHECK(lte_band_command("5", 0) != 0 && cm == 0 && efs == 0);

    reset(); write_error = 1;
    CHECK(lte_band_command("41", 0) != 0 && unlinks == 1 && cm == 0 && efs == 0);

    reset(); file_sync_error = 1;
    CHECK(lte_band_command("5", 0) != 0 && unlinks == 1 && cm == 0 && efs == 0);

    reset(); dir_sync_error = 1;
    CHECK(lte_band_command("41", 0) != 0 && cm == 0 && efs == 0);

    reset();
    CHECK(lte_band_command("41", 0) == 0 && writes == 1 && efs == 1 && restarts == 1 && cm == 0);

    reset(); stored = 1;
    CHECK(lte_band_command("5", 0) == 0 && reads == 0 && creates == 0 && cm == 1);
    CHECK(lte_band_command(0, 1) == 0 && cm == 2 && creates == 0);

    reset(); read_error = 1;
    CHECK(lte_band_command(0, 1) != 0 && cm == 0 && efs == 0);

    reset(); open_error = 1;
    CHECK(nr_band_command("78", 0) != 0 && creates == 1 && marker == 0 && efs == 0);

    reset(); write_error = 1;
    CHECK(nr_band_command("78", 0) != 0 && unlinks == 1 && marker == 0 && efs == 0);

    reset(); read_error = file_sync_error = 1;
    CHECK(nr_band_command("78", 0) != 0 && unlinks == 1 && efs == 0);

    reset(); read_error = dir_sync_error = 1;
    CHECK(nr_band_command("78", 0) != 0 && efs == 0);

    reset(); read_error = 1;
    CHECK(nr_band_command("78", 0) == 0 && marker == 1 && writes == 0);
    CHECK(file_syncs == 1 && dir_syncs == 1 && efs == 1 && restarts == 1);

    reset();
    /* No baseline, NSAccess item answers: main + NSA items both captured and
     * both written (NSA dual-write landed after the single-item era). */
    CHECK(nr_band_command("78", 0) == 0 && reads == 3 && writes == 2 && creates == 2
        && marker == 1 && efs == 2 && restarts == 1);

    reset(); stored = 1;
    /* A stored backup short-circuits the re-capture; NSA still answers. */
    CHECK(nr_band_command("78", 0) == 0 && reads == 1 && creates == 0 && efs == 2 && restarts == 1);

    reset();
    /* Dingli full-set unlock: restore rewrites the FULL set to both items and
     * restarts the modem - it never restores a dirty backup. */
    CHECK(nr_band_command(0, 1) == 0 && reads == 1 && writes == 0 && creates == 0 && efs == 2 && restarts == 1);
    return 0;
}
