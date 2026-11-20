#define _GNU_SOURCE
/* Host test: a live session creates no files; only an explicit start writes, and stop closes. */
#include <errno.h>
#include <fcntl.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <unistd.h>

static int g_fd = -1;
static char g_dir[256];
static char g_active[300];
static char g_committed[300];

static void fail(const char *msg) {
    fprintf(stderr, "diag_session_file_test: %s (errno=%d)\n", msg, errno);
    exit(1);
}

static void live_creates_nothing(void) {
    struct stat st;
    if (stat(g_active, &st) == 0) fail("live mode must not create active.qmdl");
}

static void start_session(void) {
    int fd = open(g_active, O_WRONLY | O_CREAT | O_EXCL, 0600);
    if (fd < 0) fail("start must create an empty file");
    g_fd = fd;
}

static void write_bytes(const char *data, size_t n) {
    if (g_fd < 0) fail("write without an open session");
    if (write(g_fd, data, n) != (ssize_t)n) fail("write");
}

static void stop_session(int expect_commit) {
    if (g_fd < 0) fail("stop without session");
    if (close(g_fd) != 0) fail("close");
    g_fd = -1;
    if (!expect_commit) {
        if (unlink(g_active) != 0) fail("unlink empty");
        return;
    }
    if (rename(g_active, g_committed) != 0) fail("rename to task file");
}

static void write_after_stop_is_ignored(void) {
    if (g_fd >= 0) fail("fd still open");
    /* Same guard as root_diag qmdl_write_locked: no fd, no write. */
}

int main(void) {
    snprintf(g_dir, sizeof(g_dir), "/tmp/diag-session-XXXXXX");
    if (!mkdtemp(g_dir)) fail("mkdtemp");
    snprintf(g_active, sizeof(g_active), "%s/active.qmdl", g_dir);
    snprintf(g_committed, sizeof(g_committed), "%s/task-1.qmdl", g_dir);

    live_creates_nothing();

    start_session();
    struct stat st;
    if (stat(g_active, &st) != 0 || st.st_size != 0) fail("active should start empty");
    write_bytes("QMD1", 4);
    stop_session(1);
    if (stat(g_active, &st) == 0) fail("active should be gone after finish");
    if (stat(g_committed, &st) != 0 || st.st_size != 4) fail("committed task file missing");
    write_after_stop_is_ignored();

    /* A second start must not clobber the first task file. */
    start_session();
    write_bytes("QMD2", 4);
    char second[300];
    snprintf(second, sizeof(second), "%s/task-2.qmdl", g_dir);
    if (g_fd < 0) fail("second session");
    if (close(g_fd) != 0) fail("close 2");
    g_fd = -1;
    if (rename(g_active, second) != 0) fail("rename 2");
    if (stat(g_committed, &st) != 0 || st.st_size != 4) fail("first file overwritten");
    if (stat(second, &st) != 0 || st.st_size != 4) fail("second file missing");

    unlink(g_committed);
    unlink(second);
    rmdir(g_dir);
    puts("diag_session_file_test ok");
    return 0;
}
