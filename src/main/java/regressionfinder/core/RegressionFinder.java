package regressionfinder.core;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import regressionfinder.core.renderer.ResultViewer;
import regressionfinder.model.AffectedUnit;
import regressionfinder.model.MinimalApplicableChange;

@Component
public class RegressionFinder {
		
	@Autowired
	private SourceTreeDifferencer treeDifferencer;
	
	@Autowired
	private DeltaDebugger testInvoker;
			
	@Autowired
	private ResultViewer resultViewer;
	
	
	public void run() {
		List<MinimalApplicableChange> filteredChanges = treeDifferencer.distillChanges();
		List<AffectedUnit> failureRelevantUnits = testInvoker.deltaDebug(filteredChanges);
		resultViewer.showResult(failureRelevantUnits);
	}
}
