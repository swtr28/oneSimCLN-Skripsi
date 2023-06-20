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
public class CVandTime {
    public double CV;

    public double getCL() {
        return CV;
    }

    public void setCL(double CL) {
        this.CV = CV;
    }

    public double getTime() {
        return time;
    }

    public void setTime(double time) {
        this.time = time;
    }
    public double time;
    
    public CVandTime(double cv, double t){
        CV = cv;
        time = t;
    }
}
