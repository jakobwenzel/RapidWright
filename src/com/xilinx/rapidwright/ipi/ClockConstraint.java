package com.xilinx.rapidwright.ipi;

public class ClockConstraint {
    private String clockName;
    private String period;

    public ClockConstraint(String clockName, String period) {
        this.clockName = clockName;
        this.period = period;
    }

    public String getClockName() {
        return clockName;
    }

    public void setClockName(String clockName) {
        this.clockName = clockName;
    }

    public String getPeriod() {
        return period;
    }

    public void setPeriod(String period) {
        this.period = period;
    }
}
