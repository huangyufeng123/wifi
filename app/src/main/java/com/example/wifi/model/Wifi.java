package com.example.wifi.model;

public class Wifi {
    public double wifi;
    public long time;

    public Wifi(double wifi,long time){
        this.wifi=wifi;
        this.time=time;
    }

    public double getWifi() {
        return wifi;
    }

    public long getTime() {
        return time;
    }

    public void setWifi(double wifi) {
        this.wifi = wifi;
    }

    public void setTime(long time) {
        this.time = time;
    }
}
