#include <stddef.h>
#include <stdint.h>

enum { LOCK = 1, UNLOCK, WRITE, CLOSE, ACK, MASK, OPEN };
static unsigned trace;
static int g_mu, errno, open_error, write_mode, write_calls;
static uint8_t file_bytes[64];
static size_t file_size;
typedef long long ssize_t;
typedef long long off_t;
struct stat { int st_mode; off_t st_size; };
#define S_ISREG(mode) ((mode) == 1)
#define O_WRONLY 1
#define O_CLOEXEC 2
#define O_NOFOLLOW 4
#define O_NONBLOCK 8
#define EINVAL 22
#define EIO 5
#define EINTR 4

static void step(unsigned event) { trace = (trace << 3) | event; }
static void pthread_mutex_lock(int *ignored) { step(LOCK); }
static void pthread_mutex_unlock(int *ignored) { step(UNLOCK); }
static int open(const char *path, int flags) {
    step(OPEN);
    if (open_error) { errno = open_error; return -1; }
    return 5;
}
static int fstat(int fd, struct stat *info) {
    info->st_mode = 1; info->st_size = file_size; return 0;
}
static ssize_t write(int fd, const uint8_t *data, size_t size) {
    step(WRITE);
    write_calls++;
    if (write_mode && write_calls == 1) { errno = EINTR; return -1; }
    if (write_mode && size > 4) size = 4;
    if (write_mode == 2 && write_calls == 3) { errno = 28; return -1; }
    if (size > sizeof(file_bytes) - file_size) return 0;
    for (size_t i = 0; i < size; i++) file_bytes[file_size++] = data[i];
    return (ssize_t)size;
}
static int ftruncate(int fd, off_t size) { file_size = (size_t)size; return 0; }
static int close(int fd) { step(CLOSE); return 0; }
static void qmdl_status_locked(const char *state) { step(ACK); }
static void qmdl_request_mask(int enabled) { step(MASK); }

#include "qmdl_stop_under_test.inc"

enum { DIAG_Q_SLOT = 64 };
static struct { struct { int oversized; } stats; } g_dq;
static int g_dq_mu, g_dq_cv, decoded, queued;
static uint16_t seen_codes[3];
static uint16_t rd16(const uint8_t *buf) { return buf[0] | ((uint16_t)buf[1] << 8); }
static void handle_record(const uint8_t *data, int size) { decoded++; }
static void diag_q_push(void *queue, const uint8_t *data, uint16_t size) {
    if (queued < 3) seen_codes[queued] = rd16(data + 2);
    queued++;
}
static void pthread_cond_signal(int *ignored) {}

#include "diag_live_under_test.inc"

__declspec(dllexport) int test_diag_live_without_recording(void) {
    static uint8_t packets[36] = {
        12, 0, 0xc0, 0xb0, 0, 0, 0, 0, 0, 0, 0, 0,
        12, 0, 0x0a, 0xb8, 0, 0, 0, 0, 0, 0, 0, 0,
        12, 0, 0x93, 0xb1, 0, 0, 0, 0, 0, 0, 0, 0,
    };
    if (g_qmdl_fd != -1) return __LINE__;
    on_log(packets, sizeof(packets));
    if (queued != 3 || decoded != 0) return __LINE__;
    if (seen_codes[0] != 0xb0c0 || seen_codes[1] != 0xb80a || seen_codes[2] != 0xb193) return __LINE__;
    if (g_qmdl_fd != -1 || file_size || write_calls || g_qmdl_packets || g_qmdl_pending_bytes) return __LINE__;
    trace = 0;
    return 0;
}

__declspec(dllexport) int test_qmdl_stop(void) {
    static const uint8_t record[] = { 12, 0, 0x7d, 0x7e, 0, 1, 2, 3, 4, 5, 6, 7 };
    static const uint8_t expected_frame[] = {
        0x10, 0, 12, 0, 12, 0, 0x7d, 0x5d, 0x7d, 0x5e, 0, 1, 2, 3, 4, 5, 6, 7, 5, 0x13, 0x7e
    };
    qmdl_start("active.qmdl");
    if (trace != ((LOCK << 12) | (OPEN << 9) | (ACK << 6) | (UNLOCK << 3) | MASK) || g_qmdl_fd != 5)
        return __LINE__;
    qmdl_write_locked(record, sizeof(record));
    if (g_qmdl_packets != 1 || file_size != 0) return __LINE__;
    trace = 0;
    qmdl_stop(1);
    const unsigned expected = (LOCK << 21) | (UNLOCK << 18) | (WRITE << 15) | (CLOSE << 12) |
                              (LOCK << 9) | (ACK << 6) | (UNLOCK << 3) | MASK;
    if (trace != expected || g_qmdl_fd != -1 || g_qmdl_packets != 1 || file_size != sizeof(expected_frame) ||
        g_qmdl_bytes != sizeof(expected_frame)) return __LINE__;
    for (size_t i = 0; i < file_size; i++) if (file_bytes[i] != expected_frame[i]) return __LINE__;
    trace = 0;
    qmdl_stop(1);
    if (trace != ((LOCK << 12) | (UNLOCK << 9) | (LOCK << 6) | (ACK << 3) | UNLOCK))
        return __LINE__;
    file_size = 0;
    qmdl_start("active.qmdl");
    write_mode = 1;
    write_calls = 0;
    qmdl_write_locked(record, sizeof(record));
    qmdl_stop(0);
    if (g_qmdl_error || g_qmdl_packets != 1 || file_size != sizeof(expected_frame) || write_calls <= 2)
        return __LINE__;

    file_size = 0;
    write_mode = 0;
    qmdl_start("active.qmdl");
    qmdl_write_locked(record, sizeof(record));
    qmdl_flush(g_qmdl_fd);
    qmdl_write_locked(record, sizeof(record));
    write_mode = 2;
    write_calls = 0;
    qmdl_stop(1);
    if (g_qmdl_error != 28 || g_qmdl_packets != 1 || g_qmdl_bytes != sizeof(expected_frame) ||
        file_size != sizeof(expected_frame)) return __LINE__;
    trace = 0;
    open_error = 13;
    qmdl_start("active.qmdl");
    if (trace != ((LOCK << 9) | (OPEN << 6) | (ACK << 3) | UNLOCK) || g_qmdl_fd != -1 || g_qmdl_error != 13)
        return __LINE__;
    open_error = 0;
    trace = 0;
    qmdl_start("active.qmdl");
    if (g_qmdl_error != EINVAL || g_qmdl_fd != -1 || file_size != sizeof(expected_frame)) return __LINE__;
    return 0;
}
