#include "../../main/cpp/diag_queue.c"
#include <stdio.h>
#include <string.h>

#define CHECK(cond) do { if (!(cond)) { fprintf(stderr, "FAIL %s:%d %s\n", __FILE__, __LINE__, #cond); return 1; } } while (0)

int main(void) {
    struct diag_queue q;
    struct diag_q_item it;
    uint8_t a[8] = {1,2,3,4,5,6,7,8};
    uint8_t b[4] = {9,9,9,9};
    diag_q_init(&q);
    CHECK(diag_q_empty(&q));
    CHECK(diag_q_push(&q, a, 8));
    CHECK(diag_q_push(&q, b, 4));
    CHECK(diag_q_count(&q) == 2);
    CHECK(diag_q_pop(&q, &it) && it.len == 8 && it.data[0] == 1 && it.data[7] == 8);
    CHECK(diag_q_pop(&q, &it) && it.len == 4 && it.data[0] == 9);
    CHECK(diag_q_empty(&q));
    CHECK(!diag_q_pop(&q, &it));

    /* fill then drop */
    diag_q_init(&q);
    for (int i = 0; i < DIAG_Q_SLOTS; i++) CHECK(diag_q_push(&q, a, 8));
    CHECK(!diag_q_push(&q, a, 8));
    CHECK(q.stats.dropped == 1);
    CHECK(diag_q_count(&q) == DIAG_Q_SLOTS);

    /* wrap the slot ring after draining some */
    for (int i = 0; i < 4; i++) CHECK(diag_q_pop(&q, &it));
    for (int i = 0; i < 4; i++) CHECK(diag_q_push(&q, b, 4));
    CHECK(diag_q_count(&q) == DIAG_Q_SLOTS);

    diag_q_init(&q);
    CHECK(!diag_q_push(&q, a, 0));
    CHECK(diag_q_push(&q, a, 8));
    printf("diag_queue_test ok\n");
    return 0;
}
