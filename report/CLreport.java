//package report;
//
//import java.util.List;
//
//import core.DTNHost;
//import core.SimScenario;
//import routing.ActiveRouter;
//import routing.CVandTime;
//import routing.MessageRouter;
//import routing.CVDetectionEngine;
//
//public class CLreport extends Report {
//	public CLreport() {
//		init();
//	}
//
//	public void done() {
//		List<DTNHost> hosts = SimScenario.getInstance().getHosts();
//		String write = " ";
//		for (DTNHost h : hosts) {
//			write = write + "\n----------------------------------\n"+h+"\n";
//			MessageRouter mr = h.getRouter();
//			ActiveRouter ar = (ActiveRouter) mr;
//			                 CVDetectionEngine clde = (CVDetectionEngine) ar;
//
//			List<CVandTime> cllist = clde.getCLandTime();
//			
//			if(cllist.size()!=0) {
//				for(CVandTime cl : cllist) {
//					write = write + "\n" + cl.CL+" " + cl.time;
//				}
//			}
//		}
//		write(write);
//		super.done();
//
//	}
//}