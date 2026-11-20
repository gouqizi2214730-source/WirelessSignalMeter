#include "../../main/cpp/ims_logcat.c"
#include <stdio.h>
#include <string.h>

#define CHECK(cond) do { if (!(cond)) { fprintf(stderr, "FAIL %s:%d %s\n", __FILE__, __LINE__, #cond); return 1; } } while (0)

static int parse(const char *line, int seen, struct lc_event *ev, int *fmt) {
    char red[4200];
    lc_redact_line(line, red, sizeof(red));
    *fmt = seen;
    return lc_parse_line(red, seen, fmt, ev);
}

int main(void) {
    struct lc_event ev;
    int fmt = 0;
    char red[4200];

    const char *invite_in =
        " 1700000000.000  100  101 I SIPMSG[0,2]: [<--] INVITE sip:+13800000000@[2409:8a00::1]:9000 SIP/2.0 [CSeq: 1000 INVITE]\n";
    lc_redact_line(invite_in, red, sizeof(red));
    CHECK(strstr(red, "sip:***") || strstr(red, "<n>") || strstr(red, "<ip6>"));
    CHECK(!strstr(red, "13800000000"));
    CHECK(!strstr(red, "2409:8a00"));
    CHECK(lc_keep_line(invite_in));
    CHECK(parse(invite_in, 0, &ev, &fmt) && ev.kind == LC_SIP_SAMSUNG);
    CHECK(fmt == 1 && ev.tx == 0 && !strcmp(ev.method, "INVITE") && ev.cseq == 1000 && !strcmp(ev.cseq_m, "INVITE"));

    const char *ring =
        " 1700000000.700  100  101 I SIPMSG[0,2]: [-->] SIP/2.0 180 Ringing [CSeq: 1000 INVITE]\n";
    CHECK(parse(ring, 1, &ev, &fmt) && ev.kind == LC_SIP_SAMSUNG && ev.tx == 1 && ev.code == 180);
    CHECK(!strcmp(ev.cseq_m, "INVITE"));

    const char *busy =
        " 1700000001.000  100  101 I SIPMSG[0,2]: [-->] SIP/2.0 486 Busy Here [CSeq: 1000 INVITE]\n";
    CHECK(parse(busy, 1, &ev, &fmt) && ev.code == 486);

    const char *cancel =
        " 1700000002.000  100  101 I SIPMSG[0,2]: [-->] CANCEL tel:13800000000 SIP/2.0 [CSeq: 1 CANCEL]\n";
    lc_redact_line(cancel, red, sizeof(red));
    CHECK(!strstr(red, "13800000000"));
    CHECK(parse(cancel, 1, &ev, &fmt) && !strcmp(ev.method, "CANCEL") && ev.tx == 1);

    const char *term =
        " 1700000002.060  100  101 I SIPMSG[0,2]: [<--] SIP/2.0 487 Request Terminated [CSeq: 1 INVITE]\n";
    CHECK(parse(term, 1, &ev, &fmt) && ev.code == 487);

    const char *rcs_out =
        " 1700000003.000  100  101 I SIPMSG[0,4]: [==>] REGISTER sip:*** SIP/2.0 [CSeq: 7 REGISTER]\n";
    CHECK(parse(rcs_out, 1, &ev, &fmt) && ev.kind == LC_RCS_SKIP);

    const char *rcs_in =
        " 1700000003.100  100  101 I SIPMSG[0,4]: [<==] SIP/2.0 200 OK [CSeq: 7 REGISTER]\n";
    CHECK(parse(rcs_in, 1, &ev, &fmt) && ev.kind == LC_RCS_SKIP);

    const char *reg200 =
        " 1700000000.300  100  101 I SIPMSG[0,2]: [<--] SIP/2.0 200 OK [CSeq: 2 REGISTER]\n";
    CHECK(parse(reg200, 0, &ev, &fmt) && ev.code == 200 && !strcmp(ev.cseq_m, "REGISTER"));

    const char *codec =
        " 1700000010.000  100  101 I ResipVolteHandler: onCallStateChange: audioCodec AMR-WB\n";
    CHECK(parse(codec, 1, &ev, &fmt) && ev.kind == LC_CODEC && !strcmp(ev.codec, "AMR-WB"));

    const char *csm_mo =
        " 1700000010.010  100  101 I CallStateMachine(1): bindToNetwork for MO\n";
    CHECK(parse(csm_mo, 1, &ev, &fmt) && ev.kind == LC_CALL_STATE && ev.dir == 1);

    const char *imsreg =
        " 1700000000.400  100  101 I #IMSREGI: (MMTEL) REGISTERED\n";
    CHECK(parse(imsreg, 1, &ev, &fmt) && ev.kind == LC_IMSREGI_REGISTERED);

    const char *imsreg_rcs =
        " 1700000000.410  100  101 I #IMSREGI: (RCS) REGISTERED\n";
    CHECK(!parse(imsreg_rcs, 1, &ev, &fmt));

    const char *onreg =
        " 1700000000.500  100  101 I ImsPhone: ImsRegistrationListener onRegistered\n";
    CHECK(parse(onreg, 1, &ev, &fmt) && ev.kind == LC_REGISTER_AP);

    /* Via header must not look like a SIP status before SIPMSG has been seen. */
    const char *via =
        " 1700000000.000  100  101 I SomeTag: Via: SIP/2.0/UDP 10.1.2.3:5060\n";
    lc_redact_line(via, red, sizeof(red));
    CHECK(strstr(red, "<ip>") || strstr(red, "***"));
    CHECK(!strstr(red, "10.1.2.3"));
    /* Generic matcher requires SIP/2.0 <digit><digit><digit>, not SIP/2.0/UDP */
    int seen = 0;
    CHECK(!parse(via, 0, &ev, &seen) || ev.kind != LC_SIP_GENERIC || ev.code == 0);

    printf("ims_logcat_test ok\n");
    return 0;
}
