#include "../../main/cpp/band_lock_protocol.h"
#define CHECK(condition) do { if (!(condition)) return __LINE__; } while (0)

/* Freestanding host tests do not link a C runtime. */
void *memset(void *target, int value, size_t size) {
    volatile uint8_t *p = target;
    for (size_t i = 0; i < size; i++) p[i] = (uint8_t)value;
    return target;
}

/* Can run on a host without a modem; no socket or device writes are compiled. */
__declspec(dllexport) int test_band_lock_protocol(void) {
    uint8_t data[400] = {0}, mask[64], response[120] = {0};
    CHECK(lb_bands("1,41,64", 64, mask, 8));
    CHECK(mask[0] == 1 && mask[5] == 1 && mask[7] == 128);
    CHECK(lb_bands("78,512", 512, mask, 64));
    CHECK(mask[9] == 32 && mask[63] == 128);
    CHECK(!lb_bands("0", 64, mask, 8));
    CHECK(!lb_bands("65", 64, mask, 8));
    CHECK(!lb_bands("1,", 64, mask, 8));
    CHECK(!lb_bands("1,,2", 64, mask, 8));
    CHECK(!lb_bands("1;reboot", 64, mask, 8));
    uint32_t number;
    CHECK(lb_number("0", 503, &number) && number == 0);
    CHECK(!lb_number("999999999999999", 3279165, &number));
    CHECK(!lb_number("-1", 1007, &number));
    CHECK(lb_scs(30) == 1 && lb_scs(240) < 0);
    mask[0] = 0x10;
    int size = lb_put(data, LB_LTE_BANDS, mask, 8, 1);
    CHECK(lb_read32(data) == 0x0026134b);
    CHECK(lb_read32(data + 4) == 8 && lb_read32(data + 8) == 40);
    CHECK(data[12] == 0xff && data[13] == 3 && data[14] == 0x10 && size == 62);
    size = lb_put(data, LB_NR_BANDS, mask, 64, 0);
    CHECK(lb_read32(data + 8) == 0x241 && data[12] == 0x26 && data[13] == 0x13);
    CHECK(size > 100);
    /* CM instant band frame (verified live route for LTE 1..32). */
    size = lb_cm_band(data, 0x00000010);
    CHECK(size == 36 && lb_read32(data) == 0x00280f4b);
    CHECK(lb_read32(data + 8) == 0x10 && lb_read32(data + 12) == 3);
    CHECK(lb_read32(data + 16) == 0x40000000 && lb_read32(data + 20) == 0x10);
    CHECK(lb_read32(data + 24) == 0x100 && lb_read32(data + 28) == 2 && lb_read32(data + 32) == 4);
    /* GET extraction: the ff03 marker search from the on-device captures.
     * The echoed transaction is no longer a matching key (router-side frames
     * were observed with unrelated values, e.g. 0x96 on the third frame). */
    lb_u32(response, 0x40);
    response[20] = 0xff; response[21] = 0x03; response[22] = 0x10;
    CHECK(lb_get_data(response, 30, 0x80, 8, mask) && mask[0] == 0x10);
    CHECK(lb_get_data(response, 30, 0x81, 8, mask));
    /* An echoed GET request puts the path right after ff03; refuse it. */
    response[22] = '/';
    CHECK(!lb_get_data(response, 30, 0x80, 8, mask));
    response[22] = 0x10;
    /* Short reads and non-frames fail. */
    CHECK(!lb_get_data(response, 29, 0x80, 8, mask));
    CHECK(!lb_get_data(response, 79, 0x80, 8, mask));
    uint8_t frame[64];
    /* Reply search keys on the echoed subsystem command alone. */
    lb_u32(frame, 0x40); lb_u32(frame + 4, 0x96); lb_u32(frame + 8, 3);
    lb_u32(frame + 24, 0x0026134b);
    int at = lb_find_reply(frame, 64, 0x0026134b);
    CHECK(at == 28);
    uint32_t status_out = 99;
    /* STATUS shape: plain u32, EEXIST (6) accepted when allowed. */
    lb_u32(frame + 28, 6);
    CHECK(lb_ack(frame, 64, 28, LB_ACK_STATUS, 0, 0, 0, &status_out) == 1 && status_out == 6);
    CHECK(lb_ack(frame, 64, 28, LB_ACK_STATUS, 0, 1, 0, &status_out) == 0);
    lb_u32(frame + 28, 0);
    CHECK(lb_ack(frame, 64, 28, LB_ACK_STATUS, 0, 0, 0, &status_out) == 0);
    /* PUT shape: 2-byte tag echo plus u32 status (on-wire 26 13 = 0x2613). */
    frame[28] = 0x26; frame[29] = 0x13; lb_u32(frame + 30, 0);
    CHECK(lb_ack(frame, 64, 28, LB_ACK_PUT, 0x2613, 0, 0, &status_out) == 0 && status_out == 0);
    lb_u32(frame + 30, 2);
    CHECK(lb_ack(frame, 64, 28, LB_ACK_PUT, 0x2613, 0, 0, &status_out) == 1);
    CHECK(lb_ack(frame, 64, 28, LB_ACK_PUT, 0xff03, 0, 0, &status_out) == -1);
    /* Mismatched shape stays unrecognised for the caller to keep waiting. */
    CHECK(lb_ack(frame, 64, 28, LB_ACK_STATUS, 0, 0, 0, &status_out) == -1);
    /* LOOSE accepts the echoed command itself (SYNC / CM replies). */
    CHECK(lb_ack(frame, 64, 28, LB_ACK_LOOSE, 0, 0, 0, &status_out) == 0);
    return 0;
}
