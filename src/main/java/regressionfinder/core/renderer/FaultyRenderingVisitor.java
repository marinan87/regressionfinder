package regressionfinder.core.renderer;

import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import ch.uzh.ifi.seal.changedistiller.model.entities.SourceCodeChange;
import regressionfinder.core.EvaluationContext;
import regressionfinder.core.manipulation.SourceCodeFileManipulator;
import regressionfinder.core.statistics.StatisticsTracker;
import regressionfinder.model.AffectedFile;
import regressionfinder.model.AffectedStructuralEntity;
import regressionfinder.model.CombinedPath;

@Component
public class FaultyRenderingVisitor implements RenderingVisitor {

	@Autowired
	private EvaluationContext evaluationContext;
	
	@Autowired
	private StatisticsTracker statisticsTracker;
	
	
	@Override
	public String visit(AffectedFile entity) {
		StringBuilder result = new StringBuilder();
		
		String sourceCode = evaluationContext.getFaultyProject().tryReadSourceCode(entity.getPath());
		result.append(String.format("<pre class=\"brush: java; highlight: %s\">", getLineNumbers(entity, sourceCode)));
		result.append(sourceCode);
		result.append("</pre>");
		
		return result.toString();
	}
	
	private List<Integer> getLineNumbers(AffectedFile file, String sourceCode) {
		List<SourceCodeChange> remainingChanges = new ArrayList<>(file.getChangesInFile());
		List<Integer> lines = new ArrayList<>();
		
		Scanner scanner = new Scanner(sourceCode);
		int line = 0;
		while (scanner.hasNextLine()) {
			line++;
			String nextLine = scanner.nextLine();
			
			for (SourceCodeChange change : remainingChanges) {
				String changeContent = SourceCodeFileManipulator.normalizeEntityValue(change.getChangedEntity().getContent());
				if (nextLine.contains(changeContent)) {
					lines.add(line);
					remainingChanges.remove(change);
					break;
				}	
			}
		}
		scanner.close();
		
		statisticsTracker.logNumberOfLinesToInspect(lines.size());
		return lines;
	}

	@Override
	public String visit(AffectedStructuralEntity entity) {
		String result = StringUtils.EMPTY;
		CombinedPath path = entity.getPath();
		
		switch (entity.getStructuralChangeType()) {
		case FILE_ADDED:
		case PACKAGE_ADDED:
			result = path.toString();
			break;
		default:
			result = StringUtils.EMPTY;
			break;
		}
		
		return result;
	}
}
