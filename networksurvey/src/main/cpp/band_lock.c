#define _GNU_SOURCE
#include "band_lock_protocol.h"
#include <errno.h>
#include <fcntl.h>
#include <poll.h>
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/file.h>
#include <sys/socket.h>
#include <sys/stat.h>
#include <sys/un.h>
#include <time.h>
#include <unistd.h>

static uint32_t client_id, transaction = 0x80;
static int router = -1, state_dir = -1;

static int fail(const char *message) {
    fprintf(stderr, "%s\n", message);
    return 1;
}

static long long monotonic_ms(void) {
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return (long long)ts.tv_sec * 1000 + ts.tv_nsec / 1000000;
}

static int receive(uint8_t *data, int size, int timeout) {
    struct pollfd p = { .fd = router, .events = POLLIN };
    int result;
    do { result = poll(&p, 1, timeout); } while (result < 0 && errno == EINTR);
    if (result <= 0 || !(p.revents & POLLIN)) return -1;
    return (int)recv(router, data, (size_t)size, 0);
}

static int connect_router(void) {
    router = socket(AF_UNIX, SOCK_SEQPACKET | SOCK_CLOEXEC, 0);
    if (router < 0) return fail("Cannot create DIAG socket.");
    struct sockaddr_un address = { .sun_family = AF_UNIX };
    if (connect(router, (struct sockaddr *)&address, sizeof(address)) != 0)
        return fail("This device does not expose the supported diag-router channel.");
    uint8_t reg[24] = {0}, response[8192];
    lb_u32(reg, 0x17);
    lb_u32(reg + 4, (uint32_t)getpid());
    lb_u32(reg + 8, UINT32_MAX);
    reg[12] = reg[13] = 0xff;
    lb_u32(reg + 14, 18);
    if (write(router, reg, sizeof(reg)) != sizeof(reg)) return fail("DIAG registration write failed.");
    long long deadline = monotonic_ms() + 5000;
    while (monotonic_ms() < deadline) {
        int size = receive(response, sizeof(response), 400);
        if (size >= 8 && lb_read32(response) == 0x20000) {
            client_id = lb_read32(response + 4);
            if (client_id && client_id != UINT32_MAX) return 0;
            break;
        }
    }
    return fail("DIAG did not return a valid client ID.");
}

static int send_frame(const uint8_t *payload, int size, uint32_t *tx) {
    uint8_t frame[512];
    if (size < 4 || size > (int)sizeof(frame) - 12) return 1;
    *tx = transaction++;
    lb_u32(frame, 0x40); lb_u32(frame + 4, *tx); lb_u32(frame + 8, client_id);
    memcpy(frame + 12, payload, (size_t)size);
    return write(router, frame, (size_t)size + 12) != size + 12;
}

static int exchange(const uint8_t *payload, int size, int kind, uint32_t tag,
        int allow_eexist, int allow_enoent) {
    uint32_t tx;
    if (send_frame(payload, size, &tx)) return fail("DIAG command write failed.");
    uint8_t response[8192];
    long long deadline = monotonic_ms() + 3500;
    uint32_t cmd = lb_read32(payload);
    while (monotonic_ms() < deadline) {
        int length = receive(response, sizeof(response), 350);
        if (length < 0) continue;
        int at = lb_find_reply(response, length, cmd);
        if (at < 0) continue;
        uint32_t status = 0;
        int verdict = lb_ack(response, length, at, kind, tag, allow_eexist, allow_enoent, &status);
        if (verdict < 0) continue;   /* a different reply shape; keep waiting */
        printf("ACK command=%08x kind=%d status=%u tail=%d\n", cmd, kind, status, length - at);
        if (verdict == 0) return 0;
        return fail("The modem rejected the command; see its status above.");
    }
    return fail("No recognized modem acknowledgement. The operation may be partial; refresh before retrying.");
}

static int read_mask(const char *path, int bytes, uint8_t *mask) {
    uint8_t payload[160], response[8192];
    lb_u32(payload, 0x0027134b); lb_u32(payload + 4, (uint32_t)bytes);
    lb_u32(payload + 8, (uint32_t)strlen(path) + 1);
    payload[12] = 0xff; payload[13] = 0x03;
    int size = lb_copy_path(payload, 14, path);
    uint32_t tx;
    if (send_frame(payload, size, &tx)) return 1;
    long long deadline = monotonic_ms() + 3000;
    while (monotonic_ms() < deadline) {
        int length = receive(response, sizeof(response), 300);
        if (length > 0 && lb_get_data(response, length, tx, bytes, mask)) {
            for (int i = 0; i < bytes; i++) if (mask[i]) return 0;
            return 1;
        }
    }
    return 1;
}

static int restore_mask(const char *name, int bytes, uint8_t *mask) {
    int fd = openat(state_dir, name, O_RDONLY | O_NOFOLLOW | O_CLOEXEC);
    if (fd < 0) return 1;
    struct stat st;
    int ok = fstat(fd, &st) == 0 && S_ISREG(st.st_mode) && st.st_size == bytes &&
        read(fd, mask, (size_t)bytes) == bytes;
    close(fd);
    if (ok) for (int i = 0; i < bytes; i++) if (mask[i]) return 0;
    return 1;
}

static int backup_mask(const char *path, const char *name, int bytes) {
    uint8_t original[64];
    if (!restore_mask(name, bytes, original)) return 0;
    if (read_mask(path, bytes, original))
        return fail("Cannot read the complete original band mask. No band changes were made.");
    int fd = openat(state_dir, name, O_WRONLY | O_CREAT | O_EXCL | O_NOFOLLOW | O_CLOEXEC, 0600);
    if (fd < 0) return fail("Cannot create the original band backup; no band changes were made.");
    int ok = write(fd, original, (size_t)bytes) == bytes && fsync(fd) == 0;
    close(fd);
    if (!ok) {
        unlinkat(state_dir, name, 0);
        return fail("Cannot persist the original band backup; no band changes were made.");
    }
    if (fsync(state_dir)) return fail("Cannot sync the backup directory; no band changes were made.");
    puts("Original band mask backed up.");
    return 0;
}

static int put_file(const char *dir, const char *file, const char *sync_path,
        const uint8_t *data, int bytes, uint8_t mode_a, uint8_t mode_b, int lte_band) {
    uint8_t payload[400];
    int size = lb_mkdir(payload, dir, mode_a, mode_b);
    if (exchange(payload, size, LB_ACK_STATUS, 0, 1, 0)) return 1;  /* EEXIST fine */
    size = lb_put(payload, file, data, bytes, lte_band);
    if (exchange(payload, size, LB_ACK_PUT, lte_band ? 0xff03u : 0x2613u, 0, 0)) return 1;
    size = lb_sync(payload, sync_path);
    return exchange(payload, size, LB_ACK_LOOSE, 0, 0, 0);
}

static int remove_file(const char *path, const char *dir) {
    uint8_t payload[160];
    int size = lb_unlink(payload, path);
    if (exchange(payload, size, LB_ACK_STATUS, 0, 0, 1)) return 1;  /* ENOENT fine */
    size = lb_sync(payload, dir);
    return exchange(payload, size, LB_ACK_LOOSE, 0, 0, 0);
}

static int reboot_modem(void) {
    const uint8_t frame[4] = {0x4b, 0x25, 0x03, 0x00};
    uint32_t tx;
    sleep(2);
    if (send_frame(frame, sizeof(frame), &tx)) return fail("Files were updated, but the modem restart command failed.");
    puts("RESULT restart_sent");
    puts("Modem restart requested. Wait for registration, then refresh to check the serving cell.");
    return 0;
}

/* LTE band lock: 1..32 rides the instant CM frame (the only route verified
 * live to move the serving bands on the reference device); 33..64 keeps the
 * EFS+restart route applied at modem boot. */
static int lte_band_command(const char *bands, int restore) {
    uint8_t mask[8] = {0};
    if (restore) {
        if (restore_mask("lte-original.bin", 8, mask) && read_mask(LB_LTE_BANDS, 8, mask))
            return fail("No original LTE band mask is available. A modem restart clears the instant lock.");
    } else {
        if (!lb_bands(bands, 64, mask, 8)) return fail("Invalid band list.");
        uint8_t probe[8];
        if (restore_mask("lte-original.bin", 8, probe)) {
            /* First lock on this install: keep a copy of the stored item for restore. */
            uint8_t original[8];
            if (!read_mask(LB_LTE_BANDS, 8, original))
                puts("Note: the stored LTE band item is not readable; the CM lock works without it. Restore then needs a modem restart.");
            else {
                int fd = openat(state_dir, "lte-original.bin",
                    O_WRONLY | O_CREAT | O_EXCL | O_NOFOLLOW | O_CLOEXEC, 0600);
                if (fd >= 0) {
                    int ok = write(fd, original, (size_t)8) == 8;
                    close(fd);
                    if (!ok) unlinkat(state_dir, "lte-original.bin", 0);
                    else puts("Original LTE band mask backed up.");
                }
            }
        }
    }
    for (int i = 4; i < 8; i++) if (mask[i]) {
        /* above band 32 the CM frame cannot carry it */
        if (put_file(LB_MMODE, LB_LTE_BANDS, LB_LTE_BANDS, mask, 8, 0x7f, 0x7f, 1)) return 1;
        return reboot_modem();
    }
    uint8_t frame[36];
    int size = lb_cm_band(frame, lb_read32(mask));
    if (exchange(frame, size, LB_ACK_LOOSE, 0, 0, 0)) return 1;
    puts("RESULT accepted");
    puts("Instant LTE band preference sent; verify the serving cell without restarting the modem.");
    return 0;
}

/* nr_band_pref exists on the reference device but only answers GET after a
 * first PUT materialises it: the first lock records a "no baseline" marker and
 * restoring removes the lock file so the modem rebuilds its default set. */
static int nr_marker_exists(void) {
    int fd = openat(state_dir, "nr-no-original", O_RDONLY | O_NOFOLLOW | O_CLOEXEC);
    if (fd < 0) return 0;
    close(fd);
    return 1;
}

static int nr_band_command(const char *bands, int restore) {
    uint8_t mask[64] = {0};
    if (restore) {
        if (nr_marker_exists()) {
            if (remove_file(LB_NR_BANDS, LB_MMODE)) return 1;
            return reboot_modem();
        }
        if (restore_mask("nr-original.bin", 64, mask))
            return fail("No original NR band mask is available for this device.");
    } else {
        if (!lb_bands(bands, 512, mask, 64)) return fail("Invalid band list.");
        if (backup_mask(LB_NR_BANDS, "nr-original.bin", 64)) {
            puts("Note: the original NR mask is not readable (lock item not materialised yet). Restoring will remove the lock file instead.");
            int fd = openat(state_dir, "nr-no-original",
                O_WRONLY | O_CREAT | O_EXCL | O_NOFOLLOW | O_CLOEXEC, 0600);
            if (fd < 0 && errno != EEXIST)
                return fail("Cannot record the missing baseline; no band changes were made.");
            if (fd >= 0) close(fd);
        }
    }
    if (put_file(LB_MMODE, LB_NR_BANDS, LB_MMODE, mask, 64, 0x7a, 0x7a, 0)) return 1;
    return reboot_modem();
}

static int cell_list(const char *text, uint8_t *data) {
    char list[512];
    if (strlen(text) >= sizeof(list)) return 0;
    strcpy(list, text);
    int count = 0;
    char *save;
    for (char *pair = strtok_r(list, ",", &save); pair; pair = strtok_r(NULL, ",", &save)) {
        char *colon = strchr(pair, ':');
        uint32_t channel, pci;
        if (count == 30 || !colon) return 0;
        *colon++ = 0;
        if (!lb_number(pair, 262143, &channel) || !lb_number(colon, 503, &pci)) return 0;
        lb_u32(data + 4 + count * 8, pci); lb_u32(data + 8 + count * 8, channel);
        count++;
    }
    lb_u32(data, (uint32_t)count);
    return count;
}

static int execute(int argc, char **argv) {
    const char *cmd = argv[0];
    if (!strcmp(cmd, "probe") && argc == 1) {
        uint8_t mask[64];
        puts("RESULT ready");
        printf("BACKUP lte %d\n", !restore_mask("lte-original.bin", 8, mask) ||
            !read_mask(LB_LTE_BANDS, 8, mask));
        printf("BACKUP nr %d\n", !restore_mask("nr-original.bin", 64, mask) || nr_marker_exists());
        return 0;
    }
    if ((!strcmp(cmd, "lte-band") || !strcmp(cmd, "nr-band")) && argc == 2)
        return cmd[0] == 'n' ? nr_band_command(argv[1], 0) : lte_band_command(argv[1], 0);
    if ((!strcmp(cmd, "lte-band-restore") || !strcmp(cmd, "nr-band-restore")) && argc == 1)
        return cmd[0] == 'n' ? nr_band_command(NULL, 1) : lte_band_command(NULL, 1);
    if (!strcmp(cmd, "mode") && argc == 2) {
        uint32_t mode = !strcmp(argv[1], "auto") ? 2 : !strcmp(argv[1], "lte") ? 38 :
            !strcmp(argv[1], "nr") ? 100 : 0;
        if (!mode) return fail("Invalid network mode.");
        uint8_t frame[32] = {0};
        lb_u32(frame, 0x00020f4b); lb_u32(frame + 8, mode);
        lb_u32(frame + 12, 3); lb_u32(frame + 16, 0x80000000);
        lb_u32(frame + 20, 0x100); lb_u32(frame + 24, 2); lb_u32(frame + 28, 4);
        if (exchange(frame, sizeof(frame), LB_ACK_LOOSE, 0, 0, 0)) return 1;
        puts("RESULT accepted");
        return 0;
    }
    int nr = !strncmp(cmd, "nr-", 3), lte = !strncmp(cmd, "lte-", 4);
    if (!nr && !lte) return fail("Unknown command.");
    const char *operation = strchr(cmd, '-');
    if (!operation) return 1;
    operation++;
    const char *dir = nr ? LB_NR_DIR : LB_LTE_DIR;
    if (argc == 1 && (!strcmp(operation, "freq-clear") || !strcmp(operation, "cell-clear"))) {
        int frequency = !strcmp(operation, "freq-clear");
        const char *path = nr ? (frequency ? LB_NR_FREQ : LB_NR_CELL) :
            (frequency ? LB_LTE_FREQ : LB_LTE_CELL);
        if (remove_file(path, dir)) return 1;
        if (lte && !frequency && remove_file(LB_LTE_CSP "/pci_lock", LB_LTE_CSP)) return 1;
        return reboot_modem();
    }
    uint8_t data[244] = {0};
    const char *file = NULL, *sync_path = nr ? LB_NR_DIR "/" : LB_LTE_DIR;
    int size = 0;
    uint8_t m0 = 0x7f, m1 = 0x7f;
    uint32_t frequency, pci, band, scs;
    if (!strcmp(operation, "freq") && argc == (nr ? 3 : 2)) {
        if (!lb_number(argv[1], nr ? 3279165 : 262143, &frequency)) return fail("Invalid frequency.");
        if (nr) {
            if (!frequency || !lb_number(argv[2], 120, &scs) || lb_scs(scs) < 0) return fail("Invalid NR SCS.");
            lb_u32(data, frequency); lb_u32(data + 4, (uint32_t)lb_scs(scs));
            file = LB_NR_FREQ; size = 8; m0 = 0xff; m1 = 0x1f;
        } else {
            lb_u32(data + 28, frequency); lb_u32(data + 32, frequency);
            file = LB_LTE_FREQ; size = 36; m1 = 0x6f; sync_path = LB_LTE_DIR "/";
        }
    } else if (lte && !strcmp(operation, "cell") && argc == 2) {
        int count = cell_list(argv[1], data);
        if (!count) return fail("Invalid LTE cell list.");
        file = LB_LTE_CELL; size = count > 20 ? 244 : 164; m0 = 0xff; m1 = 0x1f;
    } else if (nr && !strcmp(operation, "cell") && argc == 5) {
        if (!lb_number(argv[1], 512, &band) || !band ||
            !lb_number(argv[2], 3279165, &frequency) || !frequency ||
            !lb_number(argv[3], 1007, &pci) || !lb_number(argv[4], 120, &scs) || lb_scs(scs) < 0)
            return fail("Invalid NR cell parameters.");
        lb_u32(data, pci); lb_u32(data + 4, frequency);
        data[8] = (uint8_t)lb_scs(scs);
        data[10] = (uint8_t)(band - 1); data[11] = (uint8_t)((band - 1) >> 8);
        file = LB_NR_CELL; size = 12;
    } else return fail("Unknown command or argument count.");
    if (put_file(dir, file, sync_path, data, size, m0, m1, 0)) return 1;
    return reboot_modem();
}

int main(int argc, char **argv) {
    setvbuf(stdout, NULL, _IOLBF, 0);
    signal(SIGPIPE, SIG_IGN);
    alarm(100);
    if (getuid() != 0) return fail("Root authorization is required.");
    if (argc < 4 || strcmp(argv[1], "--state-dir")) return fail("Missing private state directory.");
    state_dir = open(argv[2], O_RDONLY | O_DIRECTORY | O_NOFOLLOW | O_CLOEXEC);
    if (state_dir < 0) return fail("Cannot open the private backup directory.");
    int lock = openat(state_dir, "operation.lock", O_RDWR | O_CREAT | O_NOFOLLOW | O_CLOEXEC, 0600);
    if (lock < 0 || flock(lock, LOCK_EX | LOCK_NB)) return fail("Another lock operation is in progress.");
    if (connect_router()) return 1;
    int result = execute(argc - 3, argv + 3);
    close(router); close(lock); close(state_dir);
    return result;
}
