package report;

import java.util.List;

import core.DTNHost;
import core.SimScenario;
import routing.ActiveRouter;
import routing.CLDetectionEngine;
import routing.CLandTime;

import routing.MessageRouter;


public class CLPerNodePerTimeReport extends Report {
	public CLPerNodePerTimeReport() {
		init();
	}

	public void done() {
		List<DTNHost> hosts = SimScenario.getInstance().getHosts();
		String write = " ";
		for (DTNHost h : hosts) {
			write = write + "\n----------------------------------\n"+h+"\n";
			MessageRouter mr =h.getRouter();
			ActiveRouter ar = (ActiveRouter) mr;
			CLDetectionEngine clde = (CLDetectionEngine) ar;

			List<CLandTime> cllist = clde.getCLandTime();
			
			if(cllist.size()!=0) {
				for(CLandTime cl : cllist) {
					write = write + "\n" +"Ini Nilai cl.CL : " + cl.CL+" "+"ini cl.Time : " + cl.time;
				}
			}
		}
		write(write);
		super.done();

	}
}
