/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package routing;

/**
 *
 * @author ASUS TUF
 */
public class CLandTime {
    public double CL;

    public double getCL() {
        return CL;
    }

    public void setCL(double CL) {
        this.CL = CL;
    }

    public double getTime() {
        return time;
    }

    public void setTime(double time) {
        this.time = time;
    }
    public double time;
    
    public CLandTime(double cl, double t){
        CL = cl;
        time = t;
    }
}
