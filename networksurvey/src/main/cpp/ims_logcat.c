#include "ims_logcat.h"
#include <ctype.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <strings.h>

static int lc_hexc(int c) { return isxdigit(c) || c == ':' || c == '.'; }

void lc_redact_line(const char *in, char *out, size_t cap) {
    size_t o = 0;
    const char *p = in;
    if (!in || !out || cap < 8) { if (out && cap) out[0] = 0; return; }
    while (*p == ' ' && o + 1 < cap) out[o++] = *p++;
    while (*p && *p != ' ' && o + 1 < cap) out[o++] = *p++;
    while (*p && o + 12 < cap) {
        int bnd = (p == in) || !isalnum((unsigned char)p[-1]);
        if (bnd && (!strncasecmp(p, "sip:", 4) || !strncasecmp(p, "tel:", 4) || !strncasecmp(p, "sips:", 5))) {
            int pl = (p[3] == ':') ? 4 : 5;
            memcpy(out + o, p, (size_t)pl); o += (size_t)pl; p += pl;
            const char *q = p;
            while (*q && !strchr(" >;\"@,]\r\n", *q)) q++;
            if (q > p) { memcpy(out + o, "***", 3); o += 3; p = q; }
            continue;
        }
        if (bnd && lc_hexc((unsigned char)*p) && *p != '.') {
            const char *q = p; int dots = 0, cols = 0, dbl = 0, alldig = 1;
            while (*q && lc_hexc((unsigned char)*q)) {
                if (*q == '.') dots++;
                else if (*q == ':') { cols++; if (q[1] == ':') dbl = 1; }
                else if (!isdigit((unsigned char)*q)) alldig = 0;
                q++;
            }
            while (q > p && (q[-1] == '.' || q[-1] == ':')) {
                if (q[-1] == '.') dots--; else cols--;
                q--;
            }
            size_t L = (size_t)(q - p);
            if (dots == 3 && cols <= 1 && alldig && L >= 7) { memcpy(out + o, "<ip>", 4); o += 4; p = q; continue; }
            if ((cols >= 3 || (dbl && cols >= 2)) && L >= 6 && dots <= 3) { memcpy(out + o, "<ip6>", 5); o += 5; p = q; continue; }
        }
        if (isdigit((unsigned char)*p) && (p == in || !isalnum((unsigned char)p[-1]) || p[-1] == '+')) {
            const char *q = p;
            while (isdigit((unsigned char)*q)) q++;
            if (q - p >= 7) { memcpy(out + o, "<n>", 3); o += 3; p = q; continue; }
            while (p < q && o + 1 < cap) out[o++] = *p++;
            continue;
        }
        out[o++] = *p++;
    }
    out[o] = 0;
}

static const char *k_lc_keys[] = {
    "SIP/2.0", "SIPMSG", "INVITE", "Ims", "IMS", "VoNR", "VoLTE", "Volte", "CallState",
    "GsmConnection", "ImsPhone", "EpsFallback", "EPSFB", "EpsFb", "RegiMgr",
    "DedicatedBearer", "IMSREGI", "IMSCR", "ResipVolte", NULL
};

int lc_keep_line(const char *line) {
    if (!line) return 0;
    for (int i = 0; k_lc_keys[i]; i++) if (strstr(line, k_lc_keys[i])) return 1;
    return 0;
}

static const char *k_lc_drop[] = {
    "SCallUI", "RCS-", ":  rec[", " DN-2", " IPF ", "MicModeManager", "AndroidMediaReceiver",
    "bluetooth", "PhoneCommonModelSet", "BWU@", "RcsCallTracker", "[MCFDs]", "NewCallSdk",
    "PhoneUtils", "TelephonyModel", "CS/InCallStateManager", "UNSOL_DATA_CALL_LIST",
    "onServiceStateChanged", "updateDataServiceState", "applyRemoteCallCapabilities",
    "applyLocalCallCapabilities", "update remoteCallProfile", "update localCallProfile",
    "onBarringInfoChanged", "handleNetworkEventBeforeRegi", "\tat ",
    "hasVideoCallOnEitherPhone", "ImsConferenceController", "updateConnectionCapabilities",
    "buildConnectionCapabilities", "refreshConferenceSupported", "shouldSetDisableAddCallExtra",
    "MSG_CONNECTION_EXTRAS_CHANGED", NULL
};

int lc_is_noisy(const char *line) {
    if (!line) return 1;
    for (int i = 0; k_lc_drop[i]; i++) if (strstr(line, k_lc_drop[i])) return 1;
    return 0;
}

static void lc_word(const char *p, char *out, size_t cap, int spaces) {
    size_t o = 0;
    if (!p || !out || !cap) return;
    while (*p && o + 1 < cap && (isalnum((unsigned char)*p) || *p == '-' || *p == '_' || *p == '.' || (spaces && *p == ' ')))
        out[o++] = *p++;
    while (o && out[o - 1] == ' ') o--;
    out[o] = 0;
}

static int parse_generic_sip(const char *l, struct lc_event *out) {
    static const char *meth[] = {
        "INVITE", "ACK", "BYE", "CANCEL", "REGISTER", "PRACK", "UPDATE", "OPTIONS",
        "SUBSCRIBE", "NOTIFY", "MESSAGE", "INFO", "REFER", "PUBLISH", NULL
    };
    const char *s;
    memset(out, 0, sizeof(*out));
    out->tx = -1;
    out->cseq = -1;
    out->kind = LC_SIP_GENERIC;
    if ((s = strstr(l, "SIP/2.0 ")) && isdigit((unsigned char)s[8]) && isdigit((unsigned char)s[9]) &&
        isdigit((unsigned char)s[10]) && (s[11] == ' ' || s[11] == 0 || s[11] == '\r' || s[11] == '\n'))
        out->code = atoi(s + 8);
    else {
        for (int i = 0; meth[i]; i++) {
            size_t ml = strlen(meth[i]);
            for (const char *q = strstr(l, meth[i]); q; q = strstr(q + 1, meth[i])) {
                if ((q == l || !isalnum((unsigned char)q[-1])) && q[ml] == ' ' &&
                    (!strncasecmp(q + ml + 1, "sip:", 4) || !strncasecmp(q + ml + 1, "tel:", 4) ||
                     !strncasecmp(q + ml + 1, "urn:", 4))) {
                    snprintf(out->method, sizeof(out->method), "%s", meth[i]);
                    break;
                }
            }
            if (out->method[0]) break;
        }
    }
    return out->code || out->method[0];
}

int lc_parse_line(const char *line, int samsung_fmt_seen, int *samsung_fmt, struct lc_event *out) {
    if (!line || !out) return 0;
    memset(out, 0, sizeof(*out));
    out->tx = -1;
    out->cseq = -1;
    long long sec = 0;
    int ms = 0, pid = 0, tid = 0, n = 0;
    char lvl = 0;
    if (sscanf(line, " %lld.%d %d %d %c %n", &sec, &ms, &pid, &tid, &lvl, &n) < 5 || !n) return 0;
    out->t_ms = sec * 1000 + ms;
    const char *tag = line + n, *msg = strstr(tag, ": ");
    if (!msg) return 0;
    msg += 2;

    if (!strncmp(tag, "SIPMSG[", 7)) {
        if (samsung_fmt) *samsung_fmt = 1;
        if (!strncmp(msg, "[-->] ", 6)) out->tx = 1;
        else if (!strncmp(msg, "[<--] ", 6)) out->tx = 0;
        else { out->kind = LC_RCS_SKIP; return 1; }
        const char *m = msg + 6;
        if (!strncmp(m, "SIP/2.0 ", 8) && isdigit((unsigned char)m[8])) {
            out->code = atoi(m + 8);
            lc_word(m + 12, out->reason, sizeof(out->reason), 1);
        } else {
            int k = 0;
            while (k < 15 && isupper((unsigned char)m[k])) { out->method[k] = m[k]; k++; }
            out->method[k] = 0;
            if (!k || m[k] != ' ') return 0;
        }
        const char *c = strstr(m, "[CSeq: ");
        if (c) {
            char *end = NULL;
            out->cseq = strtol(c + 7, &end, 10);
            c = end;
            while (*c == ' ') c++;
            lc_word(c, out->cseq_m, sizeof(out->cseq_m), 0);
        }
        if (!out->cseq_m[0] && out->method[0]) snprintf(out->cseq_m, sizeof(out->cseq_m), "%s", out->method);
        out->kind = LC_SIP_SAMSUNG;
        return 1;
    }

    const char *p;
    if (!strncmp(tag, "ResipVolteHandler", 17) && (p = strstr(msg, "audioCodec "))) {
        lc_word(p + 11, out->codec, sizeof(out->codec), 0);
        if (!out->codec[0]) return 0;
        out->kind = LC_CODEC;
        return 1;
    }
    if (!strncmp(tag, "CallStateMachine", 16)) {
        out->kind = LC_CALL_STATE;
        if ((p = strstr(msg, "ENDED Reason "))) lc_word(p + 13, out->reason, sizeof(out->reason), 1);
        else if ((p = strstr(msg, "errorCode="))) out->err = atoi(p + 10);
        else if (strstr(msg, "isVideoCRBT: true")) out->crbt_video = 1;
        else if (strstr(msg, "bindToNetwork for MO")) out->dir = 1;
        else if (strstr(msg, "bindToNetwork for MT")) out->dir = 2;
        else return 0;
        return 1;
    }
    if (!strncmp(tag, "#IMSCR", 6) && (p = strstr(msg, "Reason: SIP;cause="))) {
        out->kind = LC_IMS_CAUSE;
        out->sip_cause = atoi(p + 18);
        return 1;
    }
    if (!strncmp(tag, "ImsCallDedicatedBearer", 22) && strstr(msg, "onDedicatedBearerLost") && strstr(msg, "qci:2")) {
        out->kind = LC_BEARER_LOST_QCI2;
        return 1;
    }
    if ((p = strstr(msg, "[IMS SIP DbgMsg]")) && (p = strstr(p, ">DE-REGISTER:")) && !strstr(p, ":RCS")) {
        out->kind = LC_DEREG_CSEQ;
        out->dereg_cseq = atoi(p + 13);
        return 1;
    }
    if (!strncmp(tag, "ImsCall", 7) && (tag[7] == ' ' || tag[7] == ':') && (p = strstr(msg, "terminate :: reason="))) {
        out->kind = LC_IMS_REASON;
        out->ims_reason = atoi(p + 20);
        return 1;
    }
    if ((p = strstr(msg, "#IMSREGI - KPI"))) {
        const char *q;
        if ((q = strstr(p, "SIP Registration "))) { out->kind = LC_KPI_SIP; out->kpi = atof(q + 17); return 1; }
        if ((q = strstr(p, "Total Time "))) { out->kind = LC_KPI_TOTAL; out->kpi = atof(q + 11); return 1; }
        return 0;
    }
    if (!strncmp(tag, "#IMSREGI", 8)) {
        const char *q;
        if ((q = strstr(msg, "Airplane Mode Changed ("))) {
            out->kind = LC_AIRPLANE;
            out->airplane_on = !strncmp(q + 23, "true", 4);
            return 1;
        }
        if (msg[0] != '(' || strstr(msg, "RCS")) return 0;
        if (strstr(msg, ") DE-REGISTERED")) {
            out->kind = LC_IMSREGI_DEREGISTERED;
            q = strstr(msg, "Reason - ");
            out->reg_reason = q ? atoi(q + 9) : -1;
            return 1;
        }
        if (strstr(msg, ") REGISTERED")) { out->kind = LC_IMSREGI_REGISTERED; out->reg_reason = -1; return 1; }
        if ((q = strstr(msg, "PDN SETUP TIME : "))) { out->kind = LC_KPI_PDN; out->kpi = atof(q + 17); return 1; }
        return 0;
    }
    if (strstr(msg, "onDeregistered")) { out->kind = LC_DEREGISTER_AP; return 1; }
    if (strstr(msg, "ImsRegistrationListener onRegistered") || strstr(msg, "getImsRegistered : true")) {
        out->kind = LC_REGISTER_AP;
        return 1;
    }
    if (!samsung_fmt_seen && parse_generic_sip(line, out)) return 1;
    return 0;
}
