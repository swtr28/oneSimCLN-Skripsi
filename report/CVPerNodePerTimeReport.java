package report;

import java.util.List;

import core.DTNHost;
import core.SimScenario;
import routing.ActiveRouter;
import routing.CVandTime;
import routing.MessageRouter;
import routing.CVDetectionEngine;

public class CVPerNodePerTimeReport extends Report {
	public CVPerNodePerTimeReport() {
		init();
	}

	public void done() {
		List<DTNHost> hosts = SimScenario.getInstance().getHosts();
		String write = " ";
		for (DTNHost h : hosts) {
			write = write + "\n----------------------------------\n"+h+"\n";
			MessageRouter mr =h.getRouter();
			ActiveRouter ar = (ActiveRouter) mr;
			CVDetectionEngine cvde = (CVDetectionEngine) ar;

			List<CVandTime> cvlist = cvde.getCVandTime();
			
			if(cvlist.size()!=0) {
				for(CVandTime cv : cvlist) {
					write = write + "\n" +"Ini Nilai cl.CL : " + cv.CV+" "+"ini cl.Time : " + cv.time;
				}
			}
		}
		write(write);
		super.done();

	}
}
