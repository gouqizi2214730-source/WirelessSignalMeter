/* Samsung AP logcat IMS/SIP line parser. No I/O, no process control.
   Input should already be redacted; the parser also refuses to copy URIs. */
#ifndef IMS_LOGCAT_H
#define IMS_LOGCAT_H

#include <stddef.h>
#include <stdint.h>

enum lc_kind {
    LC_NONE = 0,
    LC_SIP_SAMSUNG,
    LC_SIP_GENERIC,
    LC_RCS_SKIP,
    LC_CODEC,
    LC_CALL_STATE,
    LC_IMS_CAUSE,
    LC_BEARER_LOST_QCI2,
    LC_DEREG_CSEQ,
    LC_IMS_REASON,
    LC_KPI_SIP,
    LC_KPI_TOTAL,
    LC_KPI_PDN,
    LC_IMSREGI_REGISTERED,
    LC_IMSREGI_DEREGISTERED,
    LC_AIRPLANE,
    LC_REGISTER_AP,
    LC_DEREGISTER_AP
};

struct lc_event {
    enum lc_kind kind;
    int64_t t_ms;
    int tx;                 /* 1 = UE sent (UL), 0 = received (DL), -1 unknown */
    int code;
    long cseq;
    int err, crbt_video, dir, sip_cause, ims_reason, dereg_cseq, airplane_on, reg_reason;
    double kpi;
    char method[16];
    char reason[32];
    char cseq_m[16];
    char codec[16];
};

void lc_redact_line(const char *in, char *out, size_t cap);
int lc_keep_line(const char *line);
int lc_is_noisy(const char *line);
/* Parse one redacted `logcat -v epoch` line. samsung_fmt_seen is 1 after any SIPMSG.
   Returns 1 if *out is filled. Sets *samsung_fmt to 1 when a SIPMSG tag is seen. */
int lc_parse_line(const char *line, int samsung_fmt_seen, int *samsung_fmt, struct lc_event *out);

#endif
