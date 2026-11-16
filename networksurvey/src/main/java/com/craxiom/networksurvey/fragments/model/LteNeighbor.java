package com.craxiom.networksurvey.fragments.model;

import static com.craxiom.networksurvey.constants.NetworkSurveyConstants.UNSET_VALUE;

/**
 * Holds the information for a single LTE neighbor record that will be displayed in the Cellular UI.
 *
 * @since 1.6.0
 */
public class LteNeighbor implements Comparable<LteNeighbor>
{
    public final int earfcn;
    public final int pci;
    public final int rsrp;
    public final int rsrq;
    public final int ta;
    public final int sinr;

    public static class Builder {
        private int earfcn = UNSET_VALUE;
        private int pci = UNSET_VALUE;
        private int rsrp = UNSET_VALUE;
        private int rsrq = UNSET_VALUE;
        private int ta = UNSET_VALUE;
        private int sinr = UNSET_VALUE;

        public Builder earfcn(int earfcn) {
            this.earfcn = earfcn;
            return this;
        }

        public Builder pci(int pci) {
            this.pci = pci;
            return this;
        }

        public Builder rsrp(int rsrp) {
            this.rsrp = rsrp;
            return this;
        }

        public Builder rsrq(int rsrq) {
            this.rsrq = rsrq;
            return this;
        }

        public Builder ta(int ta) {
            this.ta = ta;
            return this;
        }

        public LteNeighbor build() {
            return new LteNeighbor(this);
        }

        public Builder sinr(int sinr) {
            this.sinr = sinr;
            return this;
        }
    }

    private LteNeighbor(Builder builder) {
        this.earfcn = builder.earfcn;
        this.pci = builder.pci;
        this.rsrp = builder.rsrp;
        this.rsrq = builder.rsrq;
        this.ta = builder.ta;
        this.sinr = builder.sinr;
    }

    @Override
    public int compareTo(LteNeighbor neighbor)
    {
        // flipping the MAX to MIN so that unset shows up as the lowest value
        int thisComparisonRsrp = rsrp == UNSET_VALUE ? Integer.MIN_VALUE : rsrp;
        int otherComparisonRsrp = neighbor.rsrp == UNSET_VALUE ? Integer.MIN_VALUE : neighbor.rsrp;

        // Invert the sorting so the strongest show up at the top
        int strength = Integer.compare(otherComparisonRsrp, thisComparisonRsrp);
        if (strength != 0) return strength;
        int channel = Integer.compare(earfcn, neighbor.earfcn);
        return channel != 0 ? channel : Integer.compare(pci, neighbor.pci);
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        LteNeighbor neighbor = (LteNeighbor) o;

        if (earfcn != neighbor.earfcn) return false;
        if (pci != neighbor.pci) return false;
        if (rsrp != neighbor.rsrp) return false;
        if (rsrq != neighbor.rsrq) return false;
        return ta == neighbor.ta && sinr == neighbor.sinr;
    }

    @Override
    public int hashCode()
    {
        int result = earfcn;
        result = 31 * result + pci;
        result = 31 * result + rsrp;
        result = 31 * result + rsrq;
        result = 31 * result + ta;
        result = 31 * result + sinr;
        return result;
    }
}
